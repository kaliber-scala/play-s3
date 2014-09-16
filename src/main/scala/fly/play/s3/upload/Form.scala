package fly.play.s3.upload

/**
 * Utility to convert a policy to fields that can be used to construct a form
 */
case class Form(policyBuilder: PolicyBuilder) {

  def fields = {
    val policy = policyBuilder.withSignerConditions
    fieldsFromConditionsOf(policy) ++ fieldsFromPolicy(policy)
  }

  private def fieldsFromConditionsOf(policy: PolicyBuilder) =
    policy.conditions.collect {
      case Eq(name, value) => FormElement(name, value)
      case StartsWith(name, value) => FormElement(name, value, userInput = true)
    }

  private def fieldsFromPolicy(policy: PolicyBuilder) = {
    val (signatureName, Seq(signatureValue)) = policy.signer.amzSignature(policy.signature)

    Seq(
      FormElement("policy", policy.encoded),
      FormElement(signatureName, signatureValue))
  }
}

case class FormElement(name: String, value: String, userInput: Boolean = false)

