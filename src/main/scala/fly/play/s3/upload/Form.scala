package fly.play.s3.upload

import fly.play.aws.policy.AwsPolicy
import fly.play.aws.policy.StartsWith
import fly.play.aws.policy.Eq

/**
 * Utility to convert a policy to fields that can be used to construct a form
 */
case class Form(policy: AwsPolicy) {

  def fields = fieldsFromConditions ++ fieldsFromPolicy

  private def fieldsFromConditions =
    policy.conditions.collect {
      case Eq(name, value) => FormElement(name, value)
      case StartsWith(name, value) => FormElement(name, value, userInput = true)
    }

  private def fieldsFromPolicy = {
    Seq(
      FormElement("policy", policy.base64Encoded),
      FormElement(policy.signatureName, policy.signature))
  }
}

case class FormElement(name: String, value: String, userInput: Boolean = false)

