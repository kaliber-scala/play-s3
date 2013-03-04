Amazon Simple Storage Service (S3) module for Play 2.1
=====================================================

A minimal S3 API wrapper. Allows you to list, get, add and remove items from a bucket.


Installation
------------

``` scala
  val appDependencies = Seq(
    "nl.rhinofly" %% "api-s3" % "2.6.1"
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

result.map { 
	case Left(error) => throw new Exception("Error: " + x)
	case Right(success) => Logger.info("Saved the file")
}
//or
val value = Await.result(result, 10 seconds)
value.fold(
      { error => throw new Exception("Error: " + error) },
      { success => Logger.info("Saved the file") })
```      

Removing a file:

``` scala
val result = bucket - BucketFile(fileName, mimeType, byteArray)
//or
val result = bucket remove BucketFile(fileName, mimeType, byteArray)

result.map { 
	case Left(error) => throw new Exception("Error: " + x)
	case Right(success) => Logger.info("Removed the file")
}
//or
val value = Await.result(result, 10 seconds)
value.fold(
      { error => throw new Exception("Error: " + error) },
      { success => Logger.info("Removed the file") })
``` 

Retrieving a file:

``` scala
val result = bucket get "fileName"

result.map { 
	case Left(error) => throw new Exception("Error: " + x)
	case Right(BucketFile(name, contentType, content, acl, headers)) => //...
}
//or
val value = Await.result(result, 10 seconds)
value.fold(
      { error => throw new Exception("Error: " + error) },
      { file => 
      	val BucketFile(name, contentType, content, acl, headers) = file
      	//...
      })
``` 

Listing the contents of a bucket:

``` scala
val result = bucket.list

result.map {
	case Left(error) => throw new Exception("Error: " + x)
	case Right(list) => 
		list.foreach {
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

result.map { 
  case Left(error) => throw new Exception("Error: " + x)
  case Right(success) => Logger.info("Renamed the file")
}
```

More examples can be found in the `S3Spec` in the `test` folder