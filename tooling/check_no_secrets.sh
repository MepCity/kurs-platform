#!/bin/sh

set -eu

default_repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
repo_root=${REPO_ROOT:-$default_repo_root}
cd "$repo_root"

scan_files=$(mktemp "${TMPDIR:-/tmp}/kurs-platform-secret-files.XXXXXX")
violations=$(mktemp "${TMPDIR:-/tmp}/kurs-platform-secret-violations.XXXXXX")
trap 'rm -f "$scan_files" "$violations"' EXIT HUP INT TERM

find . \
  \( -type d \( -name .git -o -name build -o -name .dart_tool -o -name .gradle -o -name Pods -o -name DerivedData \) -prune \) -o \
  \( -type f -print0 \) >"$scan_files"

perl -0 -ne '
  chomp;
  my $file = $_;
  my $normalized = $file;
  $normalized =~ s{\A\./}{};

  if ($normalized ne ".env.example" &&
      $normalized =~ m{(?:\A|/)(?:\.env(?:\..*)?|.*\.(?:pem|key|p12|pfx|jks|keystore|mobileprovision))\z}) {
    print "$normalized: secret dosyası repoya eklenemez\n";
  }

  open my $fh, "<", $file or next;
  binmode $fh;
  local $/;
  my $content = <$fh>;
  close $fh;
  next if index($content, "\0") >= 0;

  my @lines = split /\n/, $content, -1;
  for my $index (0 .. $#lines) {
    my $line = $lines[$index];
    my $location = "$normalized:" . ($index + 1);
    if ($line =~ /AKIA[0-9A-Z]{16}/) {
      print "$location: AWS access key biçimi bulundu\n";
    }
    if ($line =~ /-----BEGIN [A-Z ]*PRIVATE KEY-----/) {
      print "$location: private key başlangıcı bulundu\n";
    }
    if ($line =~ /eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}/) {
      print "$location: JWT benzeri bearer değeri bulundu\n";
    }
    if ($line =~ /^\s*#?\s*[A-Z0-9_]*_REF\s*=/) {
      if ($line !~ /^\s*KURS_PLATFORM_[A-Z0-9_]*_REF\s*=\s*(development|staging|production)\/[a-z0-9][a-z0-9-]*(\/[a-z0-9][a-z0-9-]*)+\s*$/) {
        print "$location: secret referansı izinli environment/path biçiminde değil\n";
      }
      next;
    }
    if ($line =~ /^\s*#?\s*[A-Z0-9_]*(?:PASSWORD|PASSWD|SECRET|TOKEN|API_KEY|ACCESS_KEY)[A-Z0-9_]*\s*=\s*["'\'']?[A-Za-z0-9_.\/+=:\@-]{12,}/) {
      print "$location: ham secret ataması bulundu\n";
    }
    if ($line =~ /^\s*#?\s*(?:DATABASE_URL|JDBC_URL|SPRING_DATASOURCE_URL)\s*=\s*["'\'']?(?:jdbc:)?postgres(?:ql)?:\/\/[^[:space:]"'\'']+/) {
      print "$location: ham PostgreSQL bağlantı URL ataması bulundu\n";
    }
    if ($line =~ /^\s*#?\s*[A-Z0-9_]*(?:DATABASE_URL|JDBC_URL|SPRING_DATASOURCE_URL)[A-Z0-9_]*\s*=\s*["'\'']?jdbc:postgresql:\/\/[^[:space:]"'\'']+/) {
      print "$location: ham JDBC PostgreSQL bağlantı URL ataması bulundu\n";
    }
  }
' "$scan_files" >"$violations"

if [ -s "$violations" ]; then
  cat "$violations" >&2
  exit 1
fi

echo 'Repo secret taraması geçti.'
