package cn.imaq.realgps.xposed;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.widget.Toast;

import java.util.List;

public class SettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
        addPreferencesFromResource(R.xml.prefs);

        MultiSelectListPreference perAppList = (MultiSelectListPreference) findPreference("perapp_list");
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        CharSequence[] appNames = new String[apps.size()];
        CharSequence[] packageNames = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            appNames[i] = pm.getApplicationLabel(apps.get(i));
            packageNames[i] = apps.get(i).packageName;
        }
        perAppList.setEntries(appNames);
        perAppList.setEntryValues(packageNames);

        findPreference("global_port").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Toast.makeText(SettingsActivity.this, "Reboot is required for changes to take effect", Toast.LENGTH_LONG).show();
                return true;
            }
        });
    }

}
