# Cognito CloudTrail fixture kaynağı

Bu fixture'lar gerçek kullanıcı, username, parola veya token içermez. Alan şekli aşağıdaki resmî
AWS sözleşmelerinden türetilmiş ve sentetik değerlerle temizlenmiştir:

- [Amazon Cognito logging in AWS CloudTrail](https://docs.aws.amazon.com/cognito/latest/developerguide/logging-using-cloudtrail.html):
  kullanıcıya özgü eventlerde `UserName` yerine `UserSub`; CloudWatch örneğinde
  `additionalEventData.sub`.
- [CloudTrail management event record contents](https://docs.aws.amazon.com/awscloudtrail/latest/userguide/cloudtrail-event-reference-record-contents.html):
  `eventSource`, `eventType`, `eventCategory`, `managementEvent`, `recipientAccountId`,
  `awsRegion`, `errorCode` ve event kimliği alanları.
- [AWS service events delivered via CloudTrail](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-service-event-cloudtrail.html):
  SQS'ye giden EventBridge zarfındaki `AWS API Call via CloudTrail`, `source`, `account`,
  `region` ve `detail` yerleşimi.
- [RevokeToken API](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_RevokeToken.html):
  istek yalnız `ClientId` ve hassas `Token` ile kullanıcı iptal eder; `UserSub`/`UserPoolId`
  eşlemesi sunmaz.

`revoke-token.json` içindeki token değeri AWS'nin güvenli maskeleme literalidir. Parser bu alanı
okumaz ve `RevokeToken` hızlı allow-listte değildir.
