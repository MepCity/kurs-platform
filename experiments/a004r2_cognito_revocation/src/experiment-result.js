const PASS = 'PASS';
const FAIL = 'FAIL';

export function buildExperimentResult({
  initialRefreshToken,
  rotatedRefreshToken,
  reuseDetected,
  familyRevoked,
}) {
  return {
    opaqueValuesPrinted: false,
    refreshRotation:
      rotatedRefreshToken && rotatedRefreshToken !== initialRefreshToken ? PASS : FAIL,
    refreshReuseDetection: reuseDetected ? PASS : FAIL,
    completeFamilyRevocation: familyRevoked ? PASS : FAIL,
  };
}

export function experimentExitCode(result) {
  return [result.refreshRotation, result.refreshReuseDetection, result.completeFamilyRevocation]
    .every((value) => value === PASS)
    ? 0
    : 1;
}
