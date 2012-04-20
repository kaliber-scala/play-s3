package fly.play.s3

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import play.api.libs.concurrent.Promise


class S3Spec extends Specification with Before {
  
	def before = play.api.Play.start(FakeApplication())
  
	val testBucketName = "s3playlibrary.rhinofly.net"
  
		"S3" should {
		 "return an instance of bucket" in {
		   S3(testBucketName) must beAnInstanceOf[Bucket]
		 }
		}
		
		"Bucket" should {
		  var bucket = S3(testBucketName)
		  
		  "by default have / as delimiter" in {
		    bucket.delimiter must_== Some("/")
		  }
		  
		  "should be able to change delimiter" in {
		    bucket = bucket withDelimiter "-"
		    bucket.delimiter must_== Some("-")
		    
		    bucket = bucket withDelimiter None
		    bucket.delimiter must_== None
		    
		    bucket = bucket withDelimiter "/"
		    bucket.delimiter must_== Some("/")
		  }
		  
		  "give an error if we request an element that does not exist" in {
		    bucket.get("nonExistingElement").value.get.fold(
		        _ match {
		          case Error(404, "NoSuchKey", _, _) => success
		          case x => failure("Unexpected error: " + x)
		        },
		        {x => failure("Error was expected, no error received: " + x)})
		  }
		  
		  "be able to add a file" in {
		    val result = bucket + BucketFile("README.txt", "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes) 
		    result.value.get.fold({e => failure(e.toString)}, {s => success}) 
		  }
		  
		  "be able to check if it exists" in {
		    bucket.get("README.txt").value.get.fold(
		        { e => failure(e.toString) },
		        { f => f match {
		          case BucketFile("README.txt", _, _, _) => success
		          case f => failure("Wrong file returned: " + f)
		        	}
		        }
		    )
		    
		  }
		  
		  "be able add a file with a prefix" in {
		    val result = bucket + BucketFile("testPrefix/README.txt", "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes) 
		    result.value.get.fold({e => failure(e.toString)}, {s => success}) 
		  }
		  
		  "list should be iterable" in {
		    bucket.list must beAnInstanceOf[Promise[Iterable[BucketItem]]]
		  }
		  
		  "list should have a size of 2" in {
		    bucket.list.value.get.fold(
		        {e => failure(e.toString) }, 
		        { i => i.size must_== 2 })
		  }
		  
		  "list should have the correct contents" in {
		    bucket.list.value.get.fold(
		        {e => failure(e.toString)},
		        {i =>
		        	val seq = i.toSeq
		        	seq(0) match {
		        	case BucketItem("README.txt", false) => success
		        	case f => failure("Wrong file returned: " + f)
		        	}
		        	seq(1) match {
		        		case BucketItem("testPrefix/", true) => success
		        		case f => failure("Wrong file returned: " + f)
		        	}
		          
		        })
		  }
		  
		  "list with prefix should return the correct contents" in {
		    bucket.list("testPrefix/").value.get.fold(
		        { e => failure(e.toString) },
		        { i => 
		        	i.toSeq(0) match {
				      case BucketItem("testPrefix/README.txt", false) => success
				      case f => failure("Wrong file returned: " + f)
				    }
		          
		        })
		  }
		  
		  "be able to delete a file" in {
		    val result = bucket - "testPrefix/README.txt"
		    result.value.get.fold({e => failure(e.toString)}, {s => success}) 
		  }
		}
}