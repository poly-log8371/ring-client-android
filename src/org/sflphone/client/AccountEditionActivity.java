/*
 *  Copyright (C) 2004-2013 Savoir-Faire Linux Inc.
 *
 *  Author: Alexandre Lision <alexandre.lision@savoirfairelinux.com>
 *          Alexandre Savard <alexandre.savard@savoirfairelinux.com>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  If you modify this program, or any covered work, by linking or
 *  combining it with the OpenSSL project's OpenSSL library (or a
 *  modified version of that library), containing parts covered by the
 *  terms of the OpenSSL or SSLeay licenses, Savoir-Faire Linux Inc.
 *  grants you additional permission to convey the resulting work.
 *  Corresponding Source for a non-source form of such a combination
 *  shall include the source code for the parts of OpenSSL used as well
 *  as that of the covered work.
 */

package org.sflphone.client;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;

import org.sflphone.R;
import org.sflphone.account.AccountDetail;
import org.sflphone.account.AccountDetailAdvanced;
import org.sflphone.account.AccountDetailBasic;
import org.sflphone.account.AccountDetailSrtp;
import org.sflphone.account.AccountDetailTls;
import org.sflphone.service.ISipService;
import org.sflphone.service.SipService;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class AccountEditionActivity extends Activity {
    private static final String TAG = "AccoutPreferenceActivity";

    public static final String KEY_MODE = "mode";
    private boolean mBound = false;
    private ISipService service;

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            service = ISipService.Stub.asInterface(binder);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {

        }
    };

    public interface result {
        static final int ACCOUNT_MODIFIED = Activity.RESULT_FIRST_USER + 1;
        static final int ACCOUNT_DELETED = Activity.RESULT_FIRST_USER + 2;
    }

    // private ArrayList<String> requiredFields = null;
    EditionFragment mEditionFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_holder);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        mEditionFragment = new EditionFragment();
        mEditionFragment.setArguments(getIntent().getExtras());
        getFragmentManager().beginTransaction().replace(R.id.frag_container, mEditionFragment).commit();
        
        if (!mBound) {
            Log.i(TAG, "onCreate: Binding service...");
            Intent intent = new Intent(this, SipService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.account_edition, menu);
        return true;
    }

    @Override
    public void onBackPressed() {

        if (mEditionFragment != null && mEditionFragment.isDifferent) {
            AlertDialog dialog = createCancelDialog();
            dialog.show();
        } else {
            super.onBackPressed();
        }

    }

    @Override
    protected void onDestroy() {

        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }

        // stopService(new Intent(this, SipService.class));
        // serviceIsOn = false;
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        case R.id.menuitem_delete:
            AlertDialog dialog = createDeleteDialog();
            dialog.show();
            break;
        case R.id.menuitem_edit:
            processAccount(result.ACCOUNT_MODIFIED);
            break;

        }

        return true;
    }

    private void processAccount(int resultCode) {
        AlertDialog dialog;
        ArrayList<String> missingValue = new ArrayList<String>();
        if (mEditionFragment.validateAccountCreation(missingValue)) {

            HashMap<String, String> accountDetails = new HashMap<String, String>();

            updateAccountDetails(accountDetails, mEditionFragment.basicDetails);
            updateAccountDetails(accountDetails, mEditionFragment.advancedDetails);
            updateAccountDetails(accountDetails, mEditionFragment.srtpDetails);
            updateAccountDetails(accountDetails, mEditionFragment.tlsDetails);

            accountDetails.put("Account.type", "SIP");
            try {
                service.setAccountDetails(mEditionFragment.mAccountID, accountDetails);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            finish();
        } else {
            dialog = createCouldNotValidateDialog(missingValue);
            dialog.show();
        }

    }

    private void updateAccountDetails(HashMap<String, String> accountDetails, AccountDetail det) {
        for (AccountDetail.PreferenceEntry p : det.getDetailValues()) {

            Log.i(TAG, "updateAccountDetails: pref " + p.mKey + " value " + det.getDetailString(p.mKey));
            if (p.isTwoState) {
                accountDetails.put(p.mKey, det.getDetailString(p.mKey));
            } else {
                accountDetails.put(p.mKey, det.getDetailString(p.mKey));
            }
        }
    }

    /******************************************
     * 
     * AlertDialogs
     * 
     ******************************************/

    private AlertDialog createCouldNotValidateDialog(ArrayList<String> missingValue) {
        String message = "The following parameters are missing:";

        for (String s : missingValue)
            message += "\n    - " + s;

        Activity ownerActivity = this;
        AlertDialog.Builder builder = new AlertDialog.Builder(ownerActivity);
        builder.setMessage(message).setTitle("Missing Parameters").setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                /* Nothing to be done */
            }
        });

        AlertDialog alertDialog = builder.create();
        return alertDialog;
    }

    private AlertDialog createCancelDialog() {
        Activity ownerActivity = this;
        AlertDialog.Builder builder = new AlertDialog.Builder(ownerActivity);
        builder.setMessage("Modifications will be lost").setTitle("Account Edition").setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                Activity activity = ((Dialog) dialog).getOwnerActivity();
                activity.finish();
            }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                /* Terminate with no action */
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.setOwnerActivity(ownerActivity);

        return alertDialog;
    }

    private AlertDialog createDeleteDialog() {
        Activity ownerActivity = this;
        AlertDialog.Builder builder = new AlertDialog.Builder(ownerActivity);
        builder.setMessage("Do you really want to delete this account").setTitle("Delete Account")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Bundle bundle = new Bundle();
                        bundle.putString("AccountID", mEditionFragment.mAccountID);

                        try {
                            service.removeAccount(mEditionFragment.mAccountID);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        finish();
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        /* Terminate with no action */
                    }
                });

        AlertDialog alertDialog = builder.create();
        alertDialog.setOwnerActivity(ownerActivity);

        return alertDialog;
    }

    public static class EditionFragment extends PreferenceFragment {

        private AccountDetailBasic basicDetails = null;
        private AccountDetailAdvanced advancedDetails = null;
        private AccountDetailSrtp srtpDetails = null;
        private AccountDetailTls tlsDetails = null;
        private String mAccountID;
        private boolean isDifferent = false;
        private ArrayList<String> requiredFields = null;
        
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.account_creation_preferences);
            initEdition();
            requiredFields = new ArrayList<String>();
            requiredFields.add(AccountDetailBasic.CONFIG_ACCOUNT_ALIAS);
            requiredFields.add(AccountDetailBasic.CONFIG_ACCOUNT_HOSTNAME);
            requiredFields.add(AccountDetailBasic.CONFIG_ACCOUNT_USERNAME);
            requiredFields.add(AccountDetailBasic.CONFIG_ACCOUNT_PASSWORD);

        }
        
        private void initEdition() {

            Bundle b = getArguments();
            mAccountID = b.getString("AccountID");
            ArrayList<String> basicPreferenceList = b.getStringArrayList(AccountDetailBasic.BUNDLE_TAG);
            ArrayList<String> advancedPreferenceList = b.getStringArrayList(AccountDetailAdvanced.BUNDLE_TAG);
            ArrayList<String> srtpPreferenceList = b.getStringArrayList(AccountDetailSrtp.BUNDLE_TAG);
            ArrayList<String> tlsPreferenceList = b.getStringArrayList(AccountDetailTls.BUNDLE_TAG);

            basicDetails = new AccountDetailBasic(basicPreferenceList);
            advancedDetails = new AccountDetailAdvanced(advancedPreferenceList);
            srtpDetails = new AccountDetailSrtp(srtpPreferenceList);
            tlsDetails = new AccountDetailTls(tlsPreferenceList);

            setPreferenceDetails(basicDetails);
            setPreferenceDetails(advancedDetails);
            setPreferenceDetails(srtpDetails);
            setPreferenceDetails(tlsDetails);

            addPreferenceListener(basicDetails, changeBasicPreferenceListener);
            // addPreferenceListener(advancedDetails, changeAdvancedPreferenceListener);
            // addPreferenceListener(srtpDetails, changeSrtpPreferenceListener);
            // addPreferenceListener(tlsDetails, changeTlsPreferenceListener);
        }

        private void setPreferenceDetails(AccountDetail details) {
            for (AccountDetail.PreferenceEntry p : details.getDetailValues()) {
                Log.i(TAG, "setPreferenceDetails: pref " + p.mKey + " value " + p.mValue);
                Preference pref = findPreference(p.mKey);
                if (pref != null) {
                    if (p.mKey == AccountDetailAdvanced.CONFIG_LOCAL_INTERFACE) {
                        ArrayList<CharSequence> entries = new ArrayList<CharSequence>();
                        try {

                            for (Enumeration<NetworkInterface> list = NetworkInterface.getNetworkInterfaces(); list.hasMoreElements();) {
                                NetworkInterface i = list.nextElement();
                                Log.e("network_interfaces", "display name " + i.getDisplayName());
                                if (i.isUp())
                                    entries.add(i.getDisplayName());
                            }
                        } catch (SocketException e) {
                            Log.e(TAG, e.toString());
                        }
                        CharSequence[] display = new CharSequence[entries.size()];
                        entries.toArray(display);
                        ((ListPreference) pref).setEntries(display);
                        ((ListPreference) pref).setEntryValues(display);
                        pref.setSummary(p.mValue);
                        continue;
                    }
                    if (!p.isTwoState) {
                        ((EditTextPreference) pref).setText(p.mValue);
                        pref.setSummary(p.mValue);
                    }
                } else {
                    Log.w(TAG, "pref not found");
                }
            }
        }

        private void addPreferenceListener(AccountDetail details, OnPreferenceChangeListener listener) {
            for (AccountDetail.PreferenceEntry p : details.getDetailValues()) {
                Log.i(TAG, "addPreferenceListener: pref " + p.mKey + p.mValue);
                Preference pref = findPreference(p.mKey);
                if (pref != null) {

                    pref.setOnPreferenceChangeListener(listener);

                } else {
                    Log.w(TAG, "addPreferenceListener: pref not found");
                }
            }
        }

        Preference.OnPreferenceChangeListener changeBasicPreferenceListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                isDifferent = true;
                if (preference instanceof CheckBoxPreference) {
                    if ((Boolean) newValue == true)
                        basicDetails.setDetailString(preference.getKey(), ((Boolean) newValue).toString());
                } else {
                    preference.setSummary((CharSequence) newValue);
                    Log.i(TAG, "Changing preference value:" + newValue);
                    basicDetails.setDetailString(preference.getKey(), ((CharSequence) newValue).toString());
                }
                return true;
            }
        };

        Preference.OnPreferenceChangeListener changeAdvancedPreferenceListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary((CharSequence) newValue);
                advancedDetails.setDetailString(preference.getKey(), ((CharSequence) newValue).toString());
                return true;
            }
        };

        Preference.OnPreferenceChangeListener changeTlsPreferenceListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary((CharSequence) newValue);
                tlsDetails.setDetailString(preference.getKey(), ((CharSequence) newValue).toString());
                return true;
            }
        };

        Preference.OnPreferenceChangeListener changeSrtpPreferenceListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary((CharSequence) newValue);
                srtpDetails.setDetailString(preference.getKey(), ((CharSequence) newValue).toString());
                return true;
            }
        };

        public boolean validateAccountCreation(ArrayList<String> missingValue) {
            boolean valid = true;

            for (String s : requiredFields) {
                EditTextPreference pref = (EditTextPreference) findPreference(s);
                Log.i(TAG, "Looking for " + s);
                Log.i(TAG, "Value " + pref.getText());
                if (pref.getText().isEmpty()) {
                    valid = false;
                    missingValue.add(pref.getTitle().toString());
                }
            }

            return valid;
        }

    }

}