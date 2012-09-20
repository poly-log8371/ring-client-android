/*
 *  Copyright (C) 2004-2012 Savoir-Faire Linux Inc.
 *
 *  Author: Alexandre Savard <alexandre.savard@savoirfairelinux.com>
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
package com.savoirfairelinux.sflphone.client;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.*;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Profile;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.util.Log;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;

import com.savoirfairelinux.sflphone.R;

public class ContactListFragment extends ListFragment implements OnQueryTextListener, LoaderManager.LoaderCallbacks<Cursor>
{
    ContactElementAdapter mAdapter;
    String mCurFilter;

    // These are the Contacts rows that we will retrieve.
    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] { Contacts._ID, Contacts.DISPLAY_NAME,
                                                                       Contacts.PHOTO_ID, Contacts.LOOKUP_KEY };
    static final String[] CONTACTS_PHONES_PROJECTION = new String[] { Phone.NUMBER, Phone.TYPE };
    static final String[] CONTACTS_SIP_PROJECTION = new String[] { SipAddress.SIP_ADDRESS, SipAddress.TYPE };

    public static class InfosLoader implements Runnable
    {
        private View view;
        private long cid;
        private ContentResolver cr;

        public InfosLoader(Context context, View element, long contact_id)
        {
            cid = contact_id;
            cr = context.getContentResolver();
            view = element;
        }

        public static Bitmap loadContactPhoto(ContentResolver cr, long id) 
        {
            Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
            InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(cr, uri);
            if (input == null) {
                return null;
            }
            return BitmapFactory.decodeStream(input);
        }

        @Override
        public void run()
        {
            final Bitmap photo_bmp = loadContactPhoto(cr, cid);

            Cursor phones = cr.query(CommonDataKinds.Phone.CONTENT_URI,	
                                        CONTACTS_PHONES_PROJECTION, CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                        new String[] { Long.toString(cid) },
                                        null);

            final List<String> numbers = new ArrayList<String>();
            while (phones.moveToNext()) {
                String number = phones.getString(phones.getColumnIndex(CommonDataKinds.Phone.NUMBER));
                // int type = phones.getInt(phones.getColumnIndex(CommonDataKinds.Phone.TYPE));
                numbers.add(number);
            }
            phones.close();

            final Bitmap bmp = photo_bmp;
            view.post(new Runnable()
            {
                @Override
                public void run()
                {
                }
            });
        }
    }

    public static class ContactElementAdapter extends CursorAdapter
    {
        private ExecutorService infos_fetcher = Executors.newCachedThreadPool();

        public ContactElementAdapter(Context context, Cursor c)
        {
            super(context, c, 0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent)
        {
            LayoutInflater inflater = LayoutInflater.from(context);
            View v = inflater.inflate(R.layout.call_element, parent, false);
            bindView(v, context, cursor);
            return v;
        }

        @Override
        public void bindView(final View view, Context context, Cursor cursor)
        {
            final long contact_id = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));
            final String display_name = cursor.getString(cursor.getColumnIndex(Contacts.DISPLAY_NAME));
            // final long photo_uri_string = cursor.getLong(cursor.getColumnIndex(Contacts.PHOTO_ID));
            // final String photo_uri_string = cursor.getString(cursor.getColumnIndex(Contacts.PHOTO_THUMBNAIL_URI));

            TextView display_name_txt = (TextView) view.findViewById(R.id.display_name);
            display_name_txt.setText(display_name);

            ImageView photo_view = (ImageView) view.findViewById(R.id.photo);
            photo_view.setVisibility(View.GONE);

            infos_fetcher.execute(new InfosLoader(context, view, contact_id));
        }
    };

    public ContactListFragment()
    {
        super();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        // In order to onCreateOptionsMenu be called 
        setHasOptionsMenu(true);

        mAdapter = new ContactElementAdapter(getActivity(), null);
        setListAdapter(mAdapter);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.call_element_list, container, false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        // Place an action bar item for searching
        MenuItem item = menu.add("Search");
        item.setIcon(R.drawable.ic_menu_search);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        SearchView sv = new SearchView(getActivity());
        sv.setOnQueryTextListener(this);
        item.setActionView(sv);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id)
    {
        // Insert desired behavior here.
/*
        Log.i("ContactListFragment", "Contact clicked: " + contact.getDisplayName());

        SipCall call = SipCall.getCallInstance(contact);
        Log.i("ConatctListFragment", "OK");
        Log.i("ContactListFragment", "Number of calls " + SipCall.getNbCalls());

        mManager.callmanagerJNI.placeCall("IP2IP", "CALL1234", "192.168.40.35");
*/
    }

    @Override
    public boolean onQueryTextChange(String newText)
    {
        // Called when the action bar search text has changed. Update
        // the search filter, and restart the loader to do a new query
        // with this filter.
        String newFilter = !TextUtils.isEmpty(newText) ? newText : null;
        // Don't do anything if the filter hasn't actually changed.
        // Prefents restarting the loader when restoring state.
        if (mCurFilter == null && newFilter == null) { return true; }
        if (mCurFilter != null && mCurFilter.equals(newFilter)) { return true; }
        mCurFilter = newFilter;
        getLoaderManager().restartLoader(0, null, this);
        return true; 
    }

    @Override
    public boolean onQueryTextSubmit(String query)
    {
        // Return false to let the SearchView perform the default action
        return false;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args)
    {
        Uri baseUri;

        if(mCurFilter != null) {
            baseUri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(mCurFilter));
        } else {
            baseUri = Contacts.CONTENT_URI;
        }

        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        String select = "((" + Contacts.DISPLAY_NAME
                                  + " NOTNULL) AND ("
                                  + Contacts.HAS_PHONE_NUMBER
                                  + "=1) AND ("
                                  + Contacts.DISPLAY_NAME
                                  + " != '' ))";

        return new CursorLoader(getActivity(), baseUri, CONTACTS_SUMMARY_PROJECTION,
                                    select, null, Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data)
    {
        // Swap the new cursor in.
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader)
    {
        // Thi is called when the last Cursor provided to onLoadFinished 
        mAdapter.swapCursor(null);
    }
}
