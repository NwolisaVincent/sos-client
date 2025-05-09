package group.one.sos.presentation.screens.emergency_contacts

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import group.one.sos.core.constants.Tag
import group.one.sos.core.extensions.appDataStore
import group.one.sos.data.local.preferences.PreferenceKeys
import group.one.sos.domain.models.ContactModel
import group.one.sos.domain.usecases.EmergencyContactUseCases
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UiState {
    object Loading : UiState()
    object PermissionMissing : UiState()
    object LoadedContactsList : UiState()
}

@HiltViewModel
class EmergencyContactsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emergencyContactsUseCases: EmergencyContactUseCases,
) : ViewModel() {
    private val dataStore = context.appDataStore

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private val _contactsList = MutableStateFlow<PagingData<ContactModel>>(PagingData.empty())
    val contactsList: StateFlow<PagingData<ContactModel>> get() = _contactsList

    private val _searchTerm = MutableStateFlow<String>("")
    val searchTerm: StateFlow<String> = _searchTerm

    private val _selectedContact = MutableStateFlow<ContactModel?>(null)
    val selectedContact: StateFlow<ContactModel?> get() = _selectedContact

    private var hasLoadedContacts = false

    /**
     * Loads contacts from contact book.
     * 
     * A viewmodel scope is launched and we attempt to load contacts 
     * from there.
     */
    suspend fun loadContactsList() {
        if(hasLoadedContacts) {
            _uiState.value = UiState.LoadedContactsList
            return
        }

        emergencyContactsUseCases.getPagedContacts()
            .cachedIn(viewModelScope)
            .collect { pagingData ->
                _contactsList.value = pagingData
                Log.d(Tag.EmergencyContact.name, "Loaded emergency contacts")
                hasLoadedContacts = true
                _uiState.value = UiState.LoadedContactsList
            }
    }

    /**
     * Triggered when contacts permission is granted.
     *
     * Updates the permission preference on disk to prevent calling it
     * again.
     */
    fun onPermissionGranted() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            dataStore.edit { store ->
                store[PreferenceKeys.IS_CONTACTS_PERMISSION_GRANTED] = true
            }
            Log.d(Tag.EmergencyContact.name, "Contacts permission granted")
            delay(100)
            loadContactsList()
        }
    }
   
   /** Permission is missing, so request it */ 
    fun shouldRequestPermission() {
        _uiState.value = UiState.PermissionMissing
    }

    /** Updates search term to the new value */
    fun updateSearchTerm(newValue: String) {
        _searchTerm.value = newValue
    }

    /** Sets selected contact */
    fun setSelectedContact(contact: ContactModel) {
       _selectedContact.value = contact
    }


    /** Save emergency contact details to preferences datastore */
    fun saveEmergencyContactToPreferences() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            // at this point, the selected contact is assumed to exist
            dataStore.edit { store ->
                store[PreferenceKeys.EMERGENCY_CONTACT_NAME] = _selectedContact.value!!.displayName
                store[PreferenceKeys.EMERGENCY_CONTACT_NUMBER] = _selectedContact.value!!.phoneNumber
            }
            Log.d(Tag.EmergencyContact.name, "Successfully set emergency contact")
            _uiState.value = UiState.LoadedContactsList
        }
    }

}