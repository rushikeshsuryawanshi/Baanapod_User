package com.decodex.bannapod

import android.content.Intent
import android.location.Geocoder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import com.decodex.bannapod.constants.Companion.TOPIC
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

val TAG = "Main Activity"       //

var user_longitude = "NOT AVAILABLE"        //User Lat, Long
var user_latitude = "NOT AVAILABLE"

val auth = FirebaseAuth.getInstance()
val phone_no = auth.currentUser.phoneNumber

//todo support all screen sizes
//todo bug backbutton exits app


class MainActivity : AppCompatActivity() {

    lateinit var navBar_toggle: ActionBarDrawerToggle
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.app_bar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (navBar_toggle.onOptionsItemSelected(item)) {
            return true
        }

        when (item.itemId) {
            R.id.do_donts -> {
                val item_intent = Intent(this, do_donts::class.java)
                startActivity(item_intent)
            }
            R.id.about_us -> {
                val item_intent = Intent(this, about_us::class.java)
                startActivity(item_intent)
            }
        }
        return true
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //debug

        CoroutineScope(Dispatchers.IO).launch {
            var req = Firebase.firestore.collection("requests")


            var snap = req.get().await()
            for (d in snap.documents) {

                Log.d("hrishi", "siz ${d.id}")

            }
        }
        //debug

        navBar_toggle = ActionBarDrawerToggle(
            this,
            Main_DrawerLayout,
            R.string.nav_drawer_opened,
            R.string.nav_drawer_closed
        )
        Main_DrawerLayout.addDrawerListener(navBar_toggle)

        navBar_toggle.syncState()

        supportActionBar?.setDisplayHomeAsUpEnabled(true) //for backbutton after opening the drawer

        Nav_view.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.navItem_home -> {
                    val inn = Intent(this, MainActivity::class.java)
                    startActivity(inn)
                }
                R.id.navItem_doDonts -> {
                    val inn = Intent(this, do_donts::class.java)
                    inn.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(inn)

                }
                R.id.navItem_policy -> startActivity(Intent(this, MainActivity::class.java))
                R.id.navItem_aboutUs -> startActivity(Intent(this, about_us::class.java))

            }
            true //we returned out of expression. we clicked something
        }

        val time = SimpleDateFormat("dd/MM/yyyy hh:mm:ss")


        main_userLocation.text = "Location : ${getCityName(
            user_latitude.toDouble(),
            user_longitude.toDouble()
        )},  $user_latitude $user_longitude"

        getUserdata()


        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC)


        btn_sendSos.setOnClickListener {

            val request = Notification_data(
                phone_no.toString(), main_message.text.toString(), user_longitude,
                user_latitude, time.format(Date()), main_UserName.text.toString(), getCityName(
                    user_latitude.toDouble(), user_longitude.toDouble()
                )
            )

            save_request(request)


            Push_notification(
                Notification_data(
                    phone_no.toString(),
                    main_message.text.toString(),
                    user_longitude,
                    user_latitude,
                    time.format(Date())
                ),
                "/topics/admin"
            ).also {
                send_notification(it)
            }
        }

    }

    private fun send_notification(notification: Push_notification) =
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = Retrofit_instance.api.post_notification(notification)
                if (response.isSuccessful) {
                    Log.d(TAG, "Response Successful : ${Gson().toJson(response)}")
                } else {
                    Log.d(TAG, response.errorBody().toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, e.toString())

            }

        }

    private fun getUserdata() {

        val user_collection = Firebase.firestore.collection("users")

        val doc = user_collection.document(phone_no)

        doc.get().addOnSuccessListener {

            if (it.data == null) {
                auth.signOut()
                val auth_intent = Intent(this, Authentication::class.java)
                auth_intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
                startActivity(auth_intent)
                //finish()

            } else {
                val user = it.toObject<user>()
                val name = "${user!!.firstname} ${user.lastname}"
                main_UserName.text = name

            }
        }
    }

    //Takes Lat,Long , Returns City Name
    private fun getCityName(lat: Double, long: Double): String {
        var cityName = ""
        var geoCoder = Geocoder(this, Locale.getDefault())
        var Adress = geoCoder.getFromLocation(lat, long, 3)

        cityName = Adress.get(0).locality
        return cityName
    }

    //Takes in Data, Saves it to Firebase Collection
    private fun save_request(request: Notification_data) = CoroutineScope(Dispatchers.IO).launch {
        try {
            
            val requests_collection = Firebase.firestore.collection("requests")
            val time = SimpleDateFormat("dd.MM hh:mm:ss")
            requests_collection.document("$phone_no ${time.format(Date())}").set(request).addOnCompleteListener {
                Toast.makeText(this@MainActivity, "Request Sent", Toast.LENGTH_LONG).show()
            }.addOnFailureListener {
                Log.e(TAG, "Request Save Failed : ${it.message}")
                Toast.makeText(this@MainActivity, "Something Went Wrong. ${it.message}", Toast.LENGTH_LONG).show()
            }.await()

        } catch (e: Exception) {
            Log.e(TAG, "Error : ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }


}