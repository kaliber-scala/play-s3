Amazon Simple Storage Service (S3) module for Play 2.3
=====================================================

A minimal S3 API wrapper. Allows you to list, get, add and remove items from a bucket.

Has some extra features that help with direct upload and authenticated url generation.

**Note: this version uses the new aws 4 signer, this requires you to correctly set the region**


Installation
------------

``` scala
  val appDependencies = Seq(
    "nl.rhinofly" %% "play-s3" % "5.0.2"
    // use the following version for play 2.2
    //"nl.rhinofly" %% "play-s3" % "4.0.0"
    // use the following version for play 2.1
    //"nl.rhinofly" %% "play-s3" % "3.1.1"
  )

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    resolvers += "Rhinofly Internal Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local"
  )
```

Configuration
-------------

`application.conf` should contain the following information:

``` scala
aws.accessKeyId=AmazonAccessKeyId
aws.secretKey=AmazonSecretKey
```

If you are hosting in a specific region that can be specified. If you are using another S3
implementation (like riakCS), you can customize the domain name and https usage with these values:

``` scala
#default is us-east-1
s3.region="eu-west-1"
#default is determined by the region, see: http://docs.aws.amazon.com/general/latest/gr/rande.html#s3_region
s3.host="your.domain.name"
#default is false
s3.https=true
#default is false
s3.pathStyleAccess=true
```

Usage
-----

Getting a bucket:

``` scala
val bucket = S3("bucketName")
```

Adding a file:

``` scala
//not that acl and headers are optional, the default value for acl is set to PUBLIC_READ.

val result = bucket + BucketFile(fileName, mimeType, byteArray, acl, headers)
//or
val result = bucket add BucketFile(fileName, mimeType, byteArray, acl, headers)

result
  .map { unit =>
    Logger.info("Saved the file")
  }
  .recover {
    case S3Exception(status, code, message, originalXml) => Logger.info("Error: " + message)
  }

```

Removing a file:

``` scala
val result = bucket - fileName
//or
val result = bucket remove fileName

```

Retrieving a file:

``` scala
val result = bucket get "fileName"

result.map {
    case BucketFile(name, contentType, content, acl, headers) => //...
}
//or
val file = Await.result(result, 10 seconds)
val BucketFile(name, contentType, content, acl, headers) = file
```

Listing the contents of a bucket:

``` scala
val result = bucket.list

result.map { items =>
  items.map {
    case BucketItem(name, isVirtual) => //...
  }
}

//or using a prefix
val result = bucket list "prefix"
```

Retrieving a private url:

``` scala
val url = bucket.url("fileName", expirationFromNowInSeconds)
```

Renaming a file:

``` scala
val result = bucket rename("oldFileName", "newFileName", ACL)
```

Multipart file upload:

``` scala

// Retrieve an upload ticket
val result:Future[BucketFileUploadTicket] =
  bucket initiateMultipartUpload BucketFile(fileName, mimeType)

// Upload the parts and save the tickets
val result:Future[BucketFilePartUploadTicket] =
  bucket uploadPart (uploadTicket, BucketFilePart(partNumber, content))

// Complete the upload using both the upload ticket and the part upload tickets
val result:Future[Unit] =
  bucket completeMultipartUpload (uploadTicket, partUploadTickets)

```

Updating the ACL of a file:

``` scala
val result:Future[Unit] = bucket updateACL ("fileName", ACL)
```

Retrieving the ACL of a file:

``` scala
val result = testBucket.getAcl("private2README.txt")

for {
 aclList <- result
 grant <- aclList
} yield
  grant match {
    case Grant(FULL_CONTROL, CanonicalUser(id, displayName)) => //...
    case Grant(READ, Group(uri)) => //...
  }
```

Browser upload helpers:

``` scala
val `1 minute from now` = System.currentTimeMillis + (1 * 60 * 1000)

// import condition builders
import fly.play.s3.upload.Condition._

// create a policy and set the conditions
val policy =
  testBucket.uploadPolicy(expiration = new Date(`1 minute from now`))
    .withConditions(
      key startsWith "test/",
      acl eq PUBLIC_READ,
      successActionRedirect eq expectedRedirectUrl,
      header(CONTENT_TYPE) startsWith "text/",
      meta("tag").any)
    .toPolicy

// import Form helper
import fly.play.s3.upload.Form

val formFieldsFromPolicy = Form(policy).fields

// convert the form fields from the policy to an actial form
formFieldsFromPolicy
  .map {
    case FormElement(name, value, true) =>
      s"""<input type="text" name="$name" value="$value" />"""
    case FormElement(name, value, false) =>
      s"""<input type="hidden" name="$name" value="$value" />"""
  }

// make sure you add the file form field as last
val allFormFields =
  formFieldsFromPolicy.mkString("\n") +
  """<input type="text" name="file" />"""
```

More examples can be found in the `S3Spec` in the `test` folder. In order to run the tests you need
an `application.conf` file in the `test/conf` that looks like this:

``` scala
aws.accessKeyId="..."
aws.secretKey="..."

s3.region="eu-west-1"

testBucketName=s3playlibrary.rhinofly.net
```

