package com.example.test.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.test.MainActivity
import com.example.test.R
import com.example.test.data.local.database.AppDatabase
import com.example.test.data.local.entity.UserEntity
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory
import com.example.test.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var viewModel: MusicViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database = AppDatabase.getDatabase(requireContext())
        val repository = MusicRepository(database.songDao(), database.playlistDao(), database.userDao())
        val factory = MusicViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(MusicViewModel::class.java)

        binding.btnLogin.setOnClickListener {
            val identifier = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (identifier.isNotEmpty() && password.isNotEmpty()) {
                if (android.util.Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
                    // It's an email, sign in directly
                    loginWithEmail(identifier, password)
                } else {
                    // It's a username, find the email first in Firestore
                    db.collection("users")
                        .whereEqualTo("username", identifier)
                        .get()
                        .addOnSuccessListener { documents ->
                            if (!documents.isEmpty) {
                                val email = documents.documents[0].getString("email")
                                if (email != null) {
                                    loginWithEmail(email, password)
                                } else {
                                    Toast.makeText(requireContext(), "User found but email missing", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(requireContext(), "Username not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Error finding user: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvRegister.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RegisterFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun loginWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    fetchAndCacheUser(auth.currentUser?.uid ?: "")
                } else {
                    Toast.makeText(requireContext(), "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun fetchAndCacheUser(userId: String) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val username = document.getString("username") ?: ""
                    val email = document.getString("email") ?: ""
                    val profileImageUrl = document.getString("profileImageUrl") ?: ""
                    
                    val entity = UserEntity(userId, username, email, profileImageUrl)
                    viewModel.saveUser(entity)
                }
                (activity as? MainActivity)?.onLoginSuccess()
            }
            .addOnFailureListener {
                (activity as? MainActivity)?.onLoginSuccess()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
