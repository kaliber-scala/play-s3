package fly.play.aws.policy

case class AwsPolicy(
  base64Encoded: String,
  signatureName: String,
  signature: String,
  conditions: Seq[Condition])