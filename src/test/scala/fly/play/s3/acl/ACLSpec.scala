package fly.play.aws.acl

import org.specs2.mutable.Specification

object ACLSpec extends Specification {

  "ACLList" should {

    "be able to instantiate from an xml elem" in {
      val xml =
        <AccessControlList>
          <Grant>
            <Grantee xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="CanonicalUser">
              <ID>Owner-canonical-user-ID</ID>
              <DisplayName>display-name</DisplayName>
            </Grantee>
            <Permission>FULL_CONTROL</Permission>
          </Grant>
          <Grant>
            <Grantee xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="CanonicalUser">
              <ID>user1-canonical-user-ID</ID>
              <DisplayName>display-name1</DisplayName>
            </Grantee>
            <Permission>WRITE_ACP</Permission>
          </Grant>
          <Grant>
            <Grantee xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="CanonicalUser">
              <ID>user2-canonical-user-ID</ID>
              <DisplayName>display-name2</DisplayName>
            </Grantee>
            <Permission>READ_ACP</Permission>
          </Grant>
          <Grant>
            <Grantee xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="Group">
              <URI>http://acs.amazonaws.com/groups/global/AllUsers</URI>
            </Grantee>
            <Permission>READ</Permission>
          </Grant>
          <Grant>
            <Grantee xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="Group">
              <URI>http://acs.amazonaws.com/groups/s3/LogDelivery</URI>
            </Grantee>
            <Permission>WRITE</Permission>
          </Grant>
          <Grant>
            <Grantee xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="Group">
              <URI>SpecialGroup</URI>
            </Grantee>
            <Permission>WRITE</Permission>
          </Grant>
        </AccessControlList>

      ACLList(xml) ===
        Seq(
          Grant(FULL_CONTROL, CanonicalUser("Owner-canonical-user-ID", "display-name")),
          Grant(WRITE_ACP, CanonicalUser("user1-canonical-user-ID", "display-name1")),
          Grant(READ_ACP, CanonicalUser("user2-canonical-user-ID", "display-name2")),
          Grant(READ, AllUsers),
          Grant(WRITE, LogDelivery),
          Grant(WRITE, Group("SpecialGroup")))
    }
  }

}