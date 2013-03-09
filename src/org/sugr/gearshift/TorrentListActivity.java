package org.sugr.gearshift;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

import org.sugr.gearshift.TransmissionSessionManager.TransmissionExclusionStrategy;
import org.sugr.gearshift.util.Base64;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Spinner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.slidingmenu.lib.SlidingMenu;
import com.slidingmenu.lib.app.SlidingFragmentActivity;

public class TorrentListActivity extends SlidingFragmentActivity
        implements TransmissionSessionInterface, TorrentListFragment.Callbacks,
                   TorrentDetailFragment.PagerCallbacks {

    /* TODO: move to an Application class, along with the logging functions */
    public static final int PROFILES_LOADER_ID = 1;
    public static final int SESSION_LOADER_ID = 2;


    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    private static final boolean DEBUG = true;
    private static final String LogTag = "GearShift";

    private ArrayList<Torrent> mTorrents = new ArrayList<Torrent>();
    private int mCurrentTorrent = 0;

    private TransmissionProfile mProfile;
    private TransmissionSession mSession;

    private boolean mIntentConsumed = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torrent_list);

        PreferenceManager.setDefaultValues(this, R.xml.general_preferences, false);

        if (findViewById(R.id.torrent_detail_panel) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-large and
            // res/values-sw600dp). If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;

            // In two-pane mode, list items should be given the
            // 'activated' state when touched.
            ((TorrentListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.torrent_list))
                    .setActivateOnItemClick(true);

            toggleRightPane(false);
        }

        setBehindContentView(R.layout.sliding_menu_frame);

        SlidingMenu sm = getSlidingMenu();
        sm.setMode(SlidingMenu.LEFT);
        sm.setTouchModeAbove(SlidingMenu.TOUCHMODE_MARGIN);
        sm.setBehindWidthRes(R.dimen.sliding_menu_offset);
        sm.setShadowWidthRes(R.dimen.shadow_width);
        sm.setShadowDrawable(R.drawable.shadow);

        setSlidingActionBarEnabled(false);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // TODO: If exposing deep links into your app, handle intents here.
    }

    /**
     * Callback method from {@link TorrentListFragment.Callbacks}
     * indicating that the item with the given ID was selected.
     */
    @Override
    public void onItemSelected(Torrent torrent) {
        if (mTwoPane) {
            toggleRightPane(true);
            mCurrentTorrent = mTorrents.indexOf(torrent);
            FragmentManager manager = getSupportFragmentManager();
            TorrentDetailFragment fragment = (TorrentDetailFragment) manager.findFragmentByTag(
                    TorrentDetailFragment.TAG);
            if (fragment == null) {
                Bundle arguments = new Bundle();
                arguments.putInt(TorrentDetailFragment.ARG_PAGE_POSITION,
                        mCurrentTorrent);
                fragment = new TorrentDetailFragment();
                fragment.setArguments(arguments);
                manager.beginTransaction()
                        .replace(R.id.torrent_detail_container, fragment, TorrentDetailFragment.TAG)
                        .commit();
            } else {
                fragment.setCurrentTorrent(mCurrentTorrent);
            }
        } else {
            // In single-pane mode, simply start the detail activity
            // for the selected item ID.
            Intent detailIntent = new Intent(this, TorrentDetailActivity.class);
            detailIntent.putExtra(TorrentDetailFragment.ARG_PAGE_POSITION, mTorrents.indexOf(torrent));
            detailIntent.putExtra(TorrentDetailActivity.ARG_PROFILE, mProfile);
            Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
            detailIntent.putExtra(TorrentDetailActivity.ARG_JSON_TORRENTS,
                    gson.toJson(mTorrents.toArray(new Torrent[mTorrents.size()])));
            detailIntent.putExtra(TorrentDetailActivity.ARG_JSON_SESSION, gson.toJson(mSession));
            startActivity(detailIntent);
        }
    }

    @Override
    public void onPageSelected(int position) {
        if (mTwoPane) {
            ((TorrentListFragment) getSupportFragmentManager()
             .findFragmentById(R.id.torrent_list))
                .getListView().setItemChecked(position, true);
        }
    }

    @Override
    public void onBackPressed() {
        TorrentListFragment fragment = ((TorrentListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.torrent_list));

    	int position = fragment.getListView().getCheckedItemPosition();
    	if (position == ListView.INVALID_POSITION) {
    		super.onBackPressed();
    	} else {
        	toggleRightPane(false);
    		fragment.getListView().setItemChecked(position, false);
    	}
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case android.R.id.home:
            if (!mTwoPane || getSlidingMenu().isMenuShowing()) {
                toggle();
                return true;
            }

            TorrentListFragment fragment = ((TorrentListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.torrent_list));

            int position = fragment.getListView().getCheckedItemPosition();
            if (position == ListView.INVALID_POSITION) {
                toggle();
                return true;
            } else {
                toggleRightPane(false);
                fragment.getListView().setItemChecked(position, false);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean isDetailsPanelShown() {
        return mTwoPane && findViewById(R.id.torrent_detail_panel).getVisibility() == View.VISIBLE;
    }

    private boolean toggleRightPane(boolean show) {
        if (!mTwoPane) return false;

        ViewGroup panel = (ViewGroup) findViewById(R.id.torrent_detail_panel);
        if (show) {
            if (panel.getVisibility() != View.VISIBLE) {
                panel.setVisibility(View.VISIBLE);
                // LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(
                //         this, R.anim.layout_slide_right);
                // panel.setLayoutAnimation(controller);
                getSlidingMenu().setTouchModeAbove(SlidingMenu.TOUCHMODE_NONE);

                Loader<TransmissionSessionData> loader =
                        getSupportLoaderManager().getLoader(SESSION_LOADER_ID);
                if (loader != null) {
                    ((TransmissionSessionLoader) loader).setAllCurrentTorrents(true);
                }

                invalidateOptionsMenu();
                return true;
            }
        } else {
            if (panel.getVisibility() != View.GONE) {
                panel.setVisibility(View.GONE);
                getSlidingMenu().setTouchModeAbove(SlidingMenu.TOUCHMODE_MARGIN);
                Loader<TransmissionSessionData> loader = getSupportLoaderManager().getLoader(SESSION_LOADER_ID);
                if (loader != null) {
                    ((TransmissionSessionLoader) loader).setAllCurrentTorrents(false);
                }

                invalidateOptionsMenu();
                return true;
            }
        }
        return false;
    }

    public static void logE(String message, Object[] args, Exception e) {
        Log.e(LogTag, String.format(message, args), e);
    }

    public static void logE(String message, Exception e) {
        Log.e(LogTag, message, e);
    }

    public static void logD(String message, Object[] args) {
        if (!DEBUG) return;

        Log.d(LogTag, String.format(message, args));
    }

    public static void logD(String message) {
        if (!DEBUG) return;

        Log.d(LogTag, message);
    }

    public static void logDTrace() {
        if (!DEBUG) return;

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        Throwable t = new Throwable();

        t.printStackTrace(pw);
        Log.d(LogTag, sw.toString());
    }

    @Override
    public void setTorrents(ArrayList<Torrent> torrents) {
        mTorrents.clear();
        if (torrents != null) {
            mTorrents.addAll(torrents);
        }
        if (mTorrents.size() == 0) {
            toggleRightPane(false);
        }
    }

    @Override
    public ArrayList<Torrent> getTorrents() {
        return mTorrents;
    }

    @Override
    public Torrent[] getCurrentTorrents() {
        if (!isDetailsPanelShown()) return new Torrent[] {};

        return mTorrents.toArray(new Torrent[mTorrents.size()]);
    }

    @Override
    public void setProfile(TransmissionProfile profile) {
        mProfile = profile;
    }

    @Override
    public TransmissionProfile getProfile() {
        return mProfile;
    }

    @Override
    public void setSession(TransmissionSession session) {
        mSession = session;

        if (!mIntentConsumed) {
            mIntentConsumed = true;
            consumeIntent();
        }
    }

    @Override
    public TransmissionSession getSession() {
        return mSession;
    }

    @Override
    public void setRefreshing(boolean refreshing) {
        FragmentManager manager = getSupportFragmentManager();
        TorrentListFragment list = (TorrentListFragment) manager.findFragmentById(R.id.torrent_list);

        if (list != null) {
            list.setRefreshing(refreshing);
        }
    }

    private void consumeIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            final String type = intent.getType();
            final Uri data = intent.getData();

            LayoutInflater inflater = getLayoutInflater();
            View view = inflater.inflate(R.layout.add_torrent_dialog, null);
            final Loader<TransmissionSessionData> loader = getSupportLoaderManager()
                    .getLoader(TorrentListActivity.SESSION_LOADER_ID);

            final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, null)
                .setView(view);

            Spinner location;
            TransmissionProfileDirectoryAdapter adapter =
                    new TransmissionProfileDirectoryAdapter(
                    this, android.R.layout.simple_spinner_item);

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            adapter.add(getSession().getDownloadDir());
            adapter.addAll(getProfile().getDirectories());

            location = (Spinner) view.findViewById(R.id.location_choice);
            location.setAdapter(adapter);

            if (data.getScheme().equals("magnet")) {
                view.findViewById(R.id.delete_local_torrent_file).setVisibility(View.GONE);
                builder.setTitle(R.string.add_magnet).setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                        CheckBox paused = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.start_paused);

                        String dir = (String) location.getSelectedItem();
                        ((TransmissionSessionLoader) loader).addTorrent(
                                data.toString(), null, dir, paused.isChecked());

                        setRefreshing(true);
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            } else {
                new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        ContentResolver cr = getContentResolver();
                        InputStream stream = null;
                        Base64.InputStream base64 = null;
                        logD("Download uri " + data.toString());
                        try {
                            stream = cr.openInputStream(data);
                        } catch (FileNotFoundException e) {
                            logE("Error while reading the torrent file", e);
                            return null;
                        }
                        base64 = new Base64.InputStream(stream, Base64.ENCODE | Base64.DO_BREAK_LINES);
                        StringBuilder fileContent = new StringBuilder("");
                        int ch;
                        try {
                            while( (ch = base64.read()) != -1)
                              fileContent.append((char)ch);
                        } catch (IOException e) {
                            logE("Error while reading the torrent file", e);
                            return null;
                        } finally {
                            try {
                                base64.close();
                            } catch (IOException e) {
                                return null;
                            }
                        }

                        return fileContent.toString();
                    }

                    @Override
                    protected void onPostExecute(final String result) {
                        if (result == null) {
                            return;
                        }

                        builder.setTitle(R.string.add_torrent).setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                                CheckBox paused = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.start_paused);
                                CheckBox delete = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.delete_local_torrent_file);

                                String dir = (String) location.getSelectedItem();
                                ((TransmissionSessionLoader) loader).addTorrent(
                                        null, result, dir, paused.isChecked());

                                setRefreshing(true);
                            }
                        });

                        AlertDialog dialog = builder.create();
                        dialog.show();

                    }

                }.execute();
            }
        }
    }
}
