*Job opening: Scala programmer at Rhinofly*
-------------------------------------------
Each new project we start is being developed in Scala. Therefore, we are in need of a [Scala programmer](http://rhinofly.nl/vacature-scala.html) who loves to write beautiful code. No more legacy projects or maintenance of old systems of which the original programmer is already six feet under. What we need is new, fresh code for awesome projects.

Are you the Scala programmer we are looking for? Take a look at the [job description](http://rhinofly.nl/vacature-scala.html) (in Dutch) and give the Scala puzzle a try! Send us your solution and you will be invited for a job interview.
* * *

Amazon Simple Storage Service (S3) module for Play 2.2
=====================================================

A minimal S3 API wrapper. Allows you to list, get, add and remove items from a bucket.


Installation
------------

``` scala
  val appDependencies = Seq(
    "nl.rhinofly" %% "play-s3" % "3.2.1"
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

If you are using another S3 implementation (like riakCS), you can customize the domain name and 
https usage with these values:

``` scala
#default is s3.amazonaws.com
s3.host="your.domain.name"
#default is false
s3.https=true
```

Usage
-----

Getting a bucket:

``` scala
val bucket = S3("bucketName")

//with other credentials
implicit val credentials = ...
val bucket = S3("bucketName")

//or
val bucket = S3("bucketName")(credentials)
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

result.foreach {
  case BucketItem(name, isVirtual) => //...
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

More examples can be found in the `S3Spec` in the `test` folder. In order to run the tests you need 
an `application.conf` file in the `test/conf` folder containing a valid `aws.accessKeyId` and `aws.secretKey`.
