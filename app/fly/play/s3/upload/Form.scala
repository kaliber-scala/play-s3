package fly.play.s3.upload

/**
 * Utility to convert a policy to fields that can be used to construct a form
 */
case class Form(policy: PolicyBuilder) {

  lazy val fieldsFromConditions =
    policy.conditions.collect {
      case Eq(name, value) => FormElement(name, value)
      case StartsWith(name, value) => FormElement(name, value, userInput = true)
    }

  lazy val fieldsFromPolicy = Seq(
    FormElement("AWSAccessKeyId", policy.signer.credentials.accessKeyId),
    FormElement("Policy", policy.encoded),
    FormElement("Signature", policy.signature))

  lazy val fields = fieldsFromConditions ++ fieldsFromPolicy
}

case class FormElement(name: String, value: String, userInput: Boolean = false)

