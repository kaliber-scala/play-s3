package fly.play.s3

import fly.play.utils.PlayUtils._
import play.api.Play.current
import play.api.PlayException
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Promise
import play.api.libs.ws.WS
import play.api.libs.ws.Response
import play.api.libs.ws.ResponseHeaders
import play.api.mvc.RequestHeader
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang.StringUtils
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import scala.xml.Elem
import play.api.libs.iteratee.Iteratee

object S3 {

  def apply(bucketName: String): Bucket = new Bucket(bucketName)
  def apply(bucketName: String, delimiter: String): Bucket = new Bucket(bucketName, Some(delimiter))

  def hmacSha1(data: String): Array[Byte] = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(S3.keys.secret.getBytes, mac.getAlgorithm))
    mac.doFinal(data.getBytes)
  }

  def base64(data: Array[Byte]): String = new String(Base64.encodeBase64(data), "UTF-8");

  def md5(bytes: Array[Byte]): String = {
    val md = java.security.MessageDigest.getInstance("MD5")
    md.update(bytes)
    base64(md.digest)
  }

  //http://s3.amazonaws.com/doc/s3-developer-guide/RESTAuthentication.html
  def authorization(verb: String, resource: String): String =
    authorization(verb, resource, None, None, None)
  def authorization(verb: String, resource: String, amzHeaders: String, contentMd5: String, contentType: String): String =
    authorization(verb, resource, Some(amzHeaders), Some(contentMd5), Some(contentType))

  def authorization(verb: String, resource: String, amzHeaders: Option[String], contentMd5: Option[String], contentType: Option[String]): String = {

    val stringToSign =
      verb + "\n" +
        contentMd5.getOrElse("") + "\n" +
        contentType.getOrElse("") + "\n" +
        date + "\n" +
        amzHeaders.map(_ + "\n").getOrElse("") +
        resource

    //println(stringToSign)
        
    "AWS " + S3.keys.id + ":" + base64(hmacSha1(
      stringToSign))
  }

  private lazy val df = {
    val df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss '+0000'", java.util.Locale.ENGLISH)
    df.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
    df
  }
  def date: String = df.format(new Date())

  def +(bucketName: String, bucketFile: BucketFile): Promise[Response] =
    prepare(bucketName, bucketFile, "PUT").put(bucketFile.content)

  def list(bucket: Bucket): Promise[Response] =
    prepare(bucket, "GET").get()
    
  def list(bucket: Bucket, prefix:String): Promise[Response] =
	  prepare(bucket, prefix, "GET").get()

	  def get(bucket:Bucket, fileName:String):Promise[Response] = 
	    prepare(bucket.name, fileName, "GET").get()
	    
	    def -(bucketName:String, fileName:String):Promise[Response] = 
	    	prepare(bucketName, fileName, "DELETE").delete()
	  
  private def prepare(bucketName: String, bucketFile: BucketFile, verb: String): WS.WSRequestHolder = {
    val acl: String = bucketFile.acl.asString
    val contentMd5 = md5(bucketFile.content)

    WS.url("http://" + bucketName + ".s3.amazonaws.com/" + bucketFile.name)
      .withHeaders(
        "Date" -> date,
        "Authorization" -> authorization(verb, "/" + bucketName + "/" + bucketFile.name, "x-amz-acl:" + acl, contentMd5, bucketFile.contentType),
        "x-amz-acl" -> acl,
        "Content-Type" -> bucketFile.contentType,
        "Content-MD5" -> contentMd5,
        "Content-Length" -> bucketFile.content.size.toString)
  }

    private def prepare(bucketName: String, bucketFile:String, verb: String): WS.WSRequestHolder = {
    	WS.url("http://" + bucketName + ".s3.amazonaws.com/" + bucketFile)
      .withHeaders(
        "Date" -> date,
        "Authorization" -> authorization(verb, "/" + bucketName + "/" + bucketFile))
      
    }
  private def prepare(bucket: Bucket, verb: String): WS.WSRequestHolder = {

    WS.url("http://" + bucket.name + ".s3.amazonaws.com/" + bucket.delimiter.map("?delimiter=" + _).getOrElse(""))
      .withHeaders(
        "Date" -> date,
        "Authorization" -> authorization(verb, "/" + bucket.name + "/"))
  }

    private def prepare(bucket: Bucket, prefix:String, verb: String): WS.WSRequestHolder = {
    	
    	WS.url("http://" + bucket.name + ".s3.amazonaws.com/?prefix=" + prefix + bucket.delimiter.map("&delimiter=" + _).getOrElse(""))
    	.withHeaders(
    			"Date" -> date,
    			"Authorization" -> authorization(verb, "/" + bucket.name + "/"))
    }
    
  object keys {

    lazy val id = playConfiguration("s3.id").getOrElse(throw PlayException("Configuration error", "Could not find s3.id in settings"))
    lazy val secret = playConfiguration("s3.secret").getOrElse(throw PlayException("Configuration error", "Could not find s3.secret in settings"))

  }
}

case class Error(status: Int, code: String, message: String, xml:Elem)
case class Success()

class Bucket(
  val name: String,
  val delimiter: Option[String] = Some("/")) {

  def getResponse(itemName:String)(r:Response):Either[Error, BucketFile] = 
    r.status match {
       case 200 => {
        	Right(BucketFile(itemName, r.header("Content-Type").get, r.ahcResponse.getResponseBodyAsBytes))
          } 
          case status => Left(createError(status, r.xml))
  	}
  
  def get(itemName: String): Promise[Either[Error, BucketFile]] = 
    S3 get(this, itemName) map getResponse(itemName)

  def listResponse(r:Response):Either[Error, Iterable[BucketItem]] = 
    r.status match {
          case 200 => {
        	 // println(r.xml.toString)
        	val xml = r.xml
        	
        	val files = (xml \ "Contents") map(n => BucketItem(n \ "Key" text, false))
        	val folders = (xml \ "CommonPrefixes") map(n => BucketItem(n \ "Prefix" text, true))
        	
        	Right(files ++ folders)
        	
          } 
          case status => Left(createError(status, r.xml))
        }
  
  def list: Promise[Either[Error, Iterable[BucketItem]]] = 
    S3 list(this) map listResponse
  
  
  def list(prefix: String): Promise[Either[Error, Iterable[BucketItem]]] =
     S3 list(this, prefix) map listResponse

  private def createError(status: Int, xml: Elem): Error =
    xml.label match {
      case "Error" => Error(status, xml \ "Code" text, xml \ "Message" text, xml)
      case x => Error(status, "unknown body", x, xml)
    }

  def response(r:Response):Either[Error, Success] = 
    r.status match {
          case 200 | 204 => Right(Success())
          case status => println(status); println(r.ahcResponse.getHeaders); println(r.body); Left(createError(status, r.xml))
        }

  
  def +(bucketFile: BucketFile): Promise[Either[Error, Success]] =
    S3 + (name, bucketFile) map response

  def -(itemName: String): Promise[Either[Error, Success]] = 
    S3 - (name, itemName) map response

  def withDelimiter(delimiter: String): Bucket = new Bucket(name, Some(delimiter))
  def withDelimiter(delimiter: Option[String]): Bucket = new Bucket(name, delimiter)
}

case class BucketItem(name: String, isVirtual:Boolean)
case class BucketFile(name: String, contentType: String, content: Array[Byte], acl: ACL = PUBLIC_READ)

case object PUBLIC_READ extends ACL("public-read")
case object PUBLIC_READ_WRITE extends ACL("public-read-write")
case object AUTHENTICATED_READ extends ACL("authenticated-read")
case object BUCKET_OWNER_READ extends ACL("bucket-owner-read")
case object BUCKET_OWNER_FULL_CONTROL extends ACL("bucket-owner-full-control")

sealed abstract class ACL(val name: String) {
  def asString: String = name
}
