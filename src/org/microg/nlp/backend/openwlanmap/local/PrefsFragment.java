package org.microg.nlp.backend.openwlanmap.local;

import org.microg.nlp.backend.openwlanmap.R;

import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;

public class PrefsFragment extends PreferenceFragment {

	public PrefsFragment() {
		super();
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        
        CheckBoxPreference allowNetwork = (CheckBoxPreference) this.findPreference("networkAllowed");
        allowNetwork.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				
				return switchLocalGroup((Boolean) newValue);
			}
		});
        //get initial state right
        switchLocalGroup(allowNetwork.isChecked());
        
        
        EditTextPreference dbLocPreference = (EditTextPreference) this.findPreference("databaseLocation");
        if (dbLocPreference != null) {
        	//defaultValue doesn't work very well from code so we fill the pref this way
        	if (dbLocPreference.getText() == null || dbLocPreference.getText().isEmpty()) {
        		dbLocPreference.setText(Environment.getExternalStorageDirectory().getAbsolutePath() + "/.nogapps/openwifimap.db");
        	}        	
        }
    }
    
	private boolean switchLocalGroup(boolean networkAllowed) {
		
		PreferenceCategory localCategory = (PreferenceCategory) PrefsFragment.this.findPreference("category_local");
		localCategory.setEnabled(!networkAllowed);
		
		return true;
	}
}
