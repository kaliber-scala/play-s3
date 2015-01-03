package fly.play.aws

import org.specs2.mutable.Specification

object AwsErrorSpec extends Specification {
  "AwsError" should {
    "create a correct error from XML" in {
      val xml = <ErrorResponse xmlns="https://sts.amazonaws.com/doc/2011-06-15/">
                  <Error>
                    <Type>Sender</Type>
                    <Code>InvalidClientTokenId</Code>
                    <Message>The security token included in the request is invalid.</Message>
                  </Error>
                  <RequestId>6fbc48cc-c16a-11e1-bd3b-1529eff94a35</RequestId>
                </ErrorResponse>
      AwsError(403, xml) must beLike {
        case AwsError(403, "InvalidClientTokenId", "The security token included in the request is invalid.", Some(x)) if (x == xml) => ok
      }
    }
    "create a correct error from XML without the correct root element" in {
      val xml = <Error>
                  <Code>NoSuchKey</Code>
                  <Message>The specified key does not exist.</Message>
                  <Key>nonExistingElement</Key>
                  <RequestId>F5944A57D5444A0E</RequestId>
                  <HostId>z85KnjetGT4/VVHxTYLdK7ykqQxygZCVBM6dI/ALBvw93f/0eUIiDKp3V5aDr8L/</HostId>
                </Error>
      AwsError(403, xml) must beLike {
        case AwsError(403, "NoSuchKey", "The specified key does not exist.", Some(x)) if (x == xml) => ok
      }
    }
  }
}