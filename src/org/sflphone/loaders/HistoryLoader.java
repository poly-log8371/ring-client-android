package org.sflphone.loaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sflphone.model.CallContact;
import org.sflphone.model.CallContact.ContactBuilder;
import org.sflphone.model.HistoryEntry;
import org.sflphone.model.HistoryEntry.HistoryCall;
import org.sflphone.service.ISipService;
import org.sflphone.service.ServiceConstants;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

public class HistoryLoader extends AsyncTaskLoader<ArrayList<HistoryEntry>> {

    private static final String TAG = HistoryLoader.class.getSimpleName();
    private ISipService service;
    HashMap<String, HistoryEntry> historyEntries;

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] { Contacts._ID, Contacts.DISPLAY_NAME, Contacts.PHOTO_ID, Contacts.LOOKUP_KEY,
            Contacts.STARRED };

    public HistoryLoader(Context context, ISipService isip) {
        super(context);
        service = isip;
    }

    @SuppressWarnings("unchecked")
    // Hashmap runtime cast
    @Override
    public ArrayList<HistoryEntry> loadInBackground() {

        historyEntries = new HashMap<String, HistoryEntry>();

        if (service == null) {
            return new ArrayList<HistoryEntry>();
        }
        try {
            ArrayList<HashMap<String, String>> history = (ArrayList<HashMap<String, String>>) service.getHistory();
            // Log.i(TAG, "history size:" + history.size());
            CallContact.ContactBuilder builder = new CallContact.ContactBuilder();
            for (HashMap<String, String> entry : history) {
                // entry.get(ServiceConstants.history.ACCOUNT_ID_KEY);
                // long timestampEnd = Long.parseLong(entry.get(ServiceConstants.history.TIMESTAMP_STOP_KEY));
                // long timestampStart = Long.parseLong(entry.get(ServiceConstants.history.TIMESTAMP_START_KEY));
                // String call_state = entry.get(ServiceConstants.history.STATE_KEY);

                String number_called = entry.get(ServiceConstants.history.PEER_NUMBER_KEY);

                // Log.w(TAG, "----------------------Record" + entry.get(ServiceConstants.history.RECORDING_PATH_KEY));
                CallContact c = null;
                if (historyEntries.containsKey(number_called)) {
                    historyEntries.get(number_called).addHistoryCall(new HistoryCall(entry));
                } else {

                    Pattern p = Pattern.compile("<sip:([^@]+)@([^>]+)>");
                    Matcher m = p.matcher(number_called);
                    if (m.find()) {
                        // String s1 = m.group(1);
                        // System.out.println(s1);
                        // String s2 = m.group(2);
                        // System.out.println(s2);

                        Cursor result = getContext().getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                                ContactsContract.CommonDataKinds.Phone.NUMBER + " = " + m.group(1), null, null);

                        if (result.getCount() > 0) {
                            result.moveToFirst();
                            builder.startNewContact(result.getLong(result.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)),
                                    result.getString(result.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)),
                                    result.getLong(result.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_ID)));
                            builder.addPhoneNumber(number_called, 0);
                            c = builder.build();
                        } else {
                            c = ContactBuilder.buildUnknownContact(number_called);
                        }
                        result.close();
                    } else {
                        c = ContactBuilder.buildUnknownContact(number_called);
                    }
                    HistoryEntry e = new HistoryEntry(entry.get(ServiceConstants.history.ACCOUNT_ID_KEY), c);
                    e.addHistoryCall(new HistoryCall(entry));
                    historyEntries.put(number_called, e);
                }

            }

        } catch (RemoteException e) {
            Log.i(TAG, e.toString());
        }
        return new ArrayList<HistoryEntry>(historyEntries.values());
    }
}
