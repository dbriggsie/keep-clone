package controllers

import com.google.firebase
import com.google.firebase._
import com.google.firebase.auth._
import com.google.firebase.database._

import javax.inject._
import play.api._
import play.api.mvc._

import play.api.libs.json._

import org.mindrot.jbcrypt._

import models.Users

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class SettingsController @Inject() extends Controller {

  // Renders the settings page, but only when you're logged in
  def show = Action { request =>
    request.session.get("username").map { username => 
      val user = Users.getUser(username)
      
      Ok(views.html.settings(username, user))
    }.getOrElse {
      NotFound("Page not found")
    }
  }

  // Update user settings
  def update = Action { request =>
    val requestContent = request.body.asFormUrlEncoded.get

    val reqUser = request.session.get("username")

    if (reqUser.isEmpty) {
      Unauthorized("Not logged in")
    } else if (!Users.userExists(reqUser.get)) {
      Unauthorized("Not logged in as existing user")
    } else if (!requestContent.contains("fields")) {
      BadRequest("No fields provided")
    } else {
      // Get fields from request and parse them to a map
      val fields = Json.parse(requestContent("fields").head).as[Map[String, String]]
      
      // Make sure we're only setting valid fields
      val filteredFields = fields.filterKeys(_ match {
        case "email" | "firstName" | "lastName" | "password" => true
        case _ => false
      })

      // Transform required fields (which is only the password, as it has to
      // be hashed + salted)
      val finalFields = filteredFields.transform((k, v) => k match {
        case "password" => BCrypt.hashpw(v, BCrypt.gensalt())
        case k => v
      })

      // The regex used to determine whether an email is valid or not
      val emailRegex = """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r

      // Function used to validate if an email is valid, based on the regex
      def isValidEmail(e: String): Boolean = e match{
        case null => false
        case e if e.trim.isEmpty => false
        case e if emailRegex.findFirstMatchIn(e).isDefined => true
        case _ => false
      }

      // Check if the email is valid, but only if it's there
      val validEmail = if (finalFields.contains("email")) {
        isValidEmail(finalFields("email"))
      } else {
        true
      }

      // If our email is set, but not valid, give a badrequest.
      // Otherwise, put the data in Firebase.
      if (!validEmail) {
        BadRequest("Not a valid email")
      } else {
        // Get Firebase reference
        val ref = FirebaseDatabase.getInstance().getReference("keep-clone")
        val usersRef = ref.child("users")
        val currentUser = usersRef.child(reqUser.get)

        // Set all Firebase fields
        finalFields.keys.foreach(i => 
          currentUser.child(i).setValue(finalFields(i))
        )

        Ok("success")
      }
    }
  }
}
