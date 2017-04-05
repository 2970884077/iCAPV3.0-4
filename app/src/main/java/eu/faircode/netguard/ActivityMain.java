package eu.faircode.netguard;

/*
    This file is part of NetGuard.

    NetGuard is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NetGuard is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NetGuard.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2015-2016 by Marcel Bokhorst (M66B)
*/

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ActivityMain extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "NetGuard.Main";

    private boolean running = false;
    private SwipeRefreshLayout swipeRefresh;
    private DatabaseHelper dh,dh_log;
    private RuleAdapter adapter = null;
    private LogAdapter adapter_log;
    private MenuItem menuSearch = null;
    private AlertDialog dialogFirst = null;
    private AlertDialog dialogVpn = null;
    private AlertDialog dialogAbout = null;
    private boolean resolve;
    private InetAddress vpn4 = null;
    private InetAddress vpn6 = null;

    private static final int REQUEST_VPN = 1;
    private static final int REQUEST_INVITE = 2;
    private static final int REQUEST_LOGCAT = 3;
    public static final int REQUEST_ROAMING = 4;
    private static final int REQUEST_PCAP = 5;
    private static final int REQUEST_UIDPCAP = 6;

    private static final int MIN_SDK = Build.VERSION_CODES.ICE_CREAM_SANDWICH;

    public static final String ACTION_RULES_CHANGED = "eu.faircode.netguard.ACTION_RULES_CHANGED";
    public static final String EXTRA_SEARCH = "Search";
    public static final String EXTRA_APPROVE = "Approve";

    private ListView lvLog;

    private DatabaseHelper.LogChangedListener listener = new DatabaseHelper.LogChangedListener() {
        @Override
        public void onChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateAdapter();
                }
            });
        }
    };

    private TextView tv_mobileTraffic;
    private TextView tv_wifiTraffic;

    private Button button;
    private EditText editText2;
    private TextView textView8;

    int switchon;

    //private ProgressBar progressBar;
    //public TextView textView11;
   // public TextView textView10;

    //public NumberPicker numberPicker;

    //private static final int REQUEST_PCAP = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Create");



        if (Build.VERSION.SDK_INT < MIN_SDK) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.android);
            return;
        }

        Util.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        running = true;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = prefs.getBoolean("enabled", false);
        boolean initialized = prefs.getBoolean("initialized", false);

        // Upgrade
        Receiver.upgrade(initialized, this);

        if (!getIntent().hasExtra(EXTRA_APPROVE)) {
            if (enabled)
                SinkholeService.start("UI", this);
            else
                SinkholeService.stop("UI", this);
        }

        // Action bar
        View actionView = getLayoutInflater().inflate(R.layout.action, null);
        SwitchCompat swEnabled = (SwitchCompat) actionView.findViewById(R.id.swEnabled);

        getSupportActionBar().setDisplayShowCustomEnabled(true);
        getSupportActionBar().setCustomView(actionView);

        // On/off switch
        swEnabled.setChecked(enabled);
        swEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.i(TAG, "Switch=" + isChecked);
                if (isChecked){
                    switchon = 1;
                    Input_switchon(switchon);
                }
                else {
                    switchon = 2;
                    Input_switchon(switchon);
                }
                prefs.edit().putBoolean("enabled", isChecked).apply();

                if (isChecked) {


                    //Toast.makeText(ActivityMain.this, Integer.toString(switchon), Toast.LENGTH_SHORT);
                    try {
                        final Intent prepare = VpnService.prepare(ActivityMain.this);
                        if (prepare == null) {
                            Log.i(TAG, "Prepare done");
                            onActivityResult(REQUEST_VPN, RESULT_OK, null);
                        } else {
                            // Show dialog
                            LayoutInflater inflater = LayoutInflater.from(ActivityMain.this);
                            View view = inflater.inflate(R.layout.vpn, null);
                            dialogVpn = new AlertDialog.Builder(ActivityMain.this)
                                    .setView(view)
                                    .setCancelable(false)
                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (running) {
                                                Log.i(TAG, "Start intent=" + prepare);
                                                try {
                                                    startActivityForResult(prepare, REQUEST_VPN);
                                                } catch (Throwable ex) {
                                                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                                                    Util.sendCrashReport(ex, ActivityMain.this);
                                                    onActivityResult(REQUEST_VPN, RESULT_CANCELED, null);
                                                    prefs.edit().putBoolean("enabled", false).apply();
                                                }
                                            }
                                        }
                                    })
                                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                        @Override
                                        public void onDismiss(DialogInterface dialogInterface) {
                                            dialogVpn = null;
                                        }
                                    })
                                    .create();
                            dialogVpn.show();
                        }
                    } catch (Throwable ex) {
                        // Prepare failed
                        Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        Util.sendCrashReport(ex, ActivityMain.this);
                        prefs.edit().putBoolean("enabled", false).apply();
                    }

                } else
                    SinkholeService.stop("switch off", ActivityMain.this);
            }
        });


        // Total Traffic
        //tv_mobileTraffic = (TextView) findViewById(R.id.mobile_traffic);
        //tv_wifiTraffic = (TextView) findViewById(R.id.wifi_traffic);

        //Toast.makeText(ActivityMain.this, Integer.toString(switchon), Toast.LENGTH_SHORT);

        //int mobileTraffic1 = Integer.valueOf(tv_mobileTraffic.getText().toString());


       // progressBar = (ProgressBar)findViewById(R.id.progressBar);
        //textView11 = (TextView)findViewById(R.id.textView11);
        //textView10 = (TextView)findViewById(R.id.textView10);

       // textView8 = (TextView)findViewById(R.id.textView8);

        //editText2 = (EditText)findViewById(R.id.editText2);
        //button = (Button)findViewById(R.id.button);

        /*button.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                //textView8.setText(editText2.getText());


                int Traffic = Integer.valueOf(editText2.getText().toString());
                progressBar.setMax(Traffic);
                progressBar.setProgress((int) ((TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()) / 1024f / 1024f));



                textView11.setText(getString(R.string.msg_mbdaymobilePercent, ((TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()) / 1024f / 1024f) / (Traffic) * 100));
                //textView10.setText(getString(R.string.msg_mbdaymobileUsage, (Traffic-((TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()) / 1024f / 1024f))));

            }


        });*/


        //int[] to_layout = new int[]{R.id.textView,R.id.mobile_traffic};

        //SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.list_row, Cursor , from_column , to_layout);
        resolve = prefs.getBoolean("resolve", false);

        lvLog = (ListView) findViewById(R.id.lvLog);

        boolean udp = prefs.getBoolean("proto_udp", true);
        boolean tcp = prefs.getBoolean("proto_tcp", true);
        boolean other = prefs.getBoolean("proto_other", true);
        boolean allowed = prefs.getBoolean("traffic_allowed", true);
        boolean blocked = prefs.getBoolean("traffic_blocked", true);

        dh_log = new DatabaseHelper(this);
        adapter_log = new LogAdapter(this, dh_log.getLog(udp, tcp, other, allowed, blocked), resolve);
        adapter_log.setFilterQueryProvider(new FilterQueryProvider() {
            public Cursor runQuery(CharSequence constraint) {
                return dh_log.searchLog(constraint.toString());
            }
        });


        dh_log.addLogChangedListener(listener);
        updateAdapter();

        lvLog.setAdapter(adapter_log);

        try {
            vpn4 = InetAddress.getByName(prefs.getString("vpn4", "10.1.10.1"));
            vpn6 = InetAddress.getByName(prefs.getString("vpn6", "fd00:1:fd00:1:fd00:1:fd00:1"));
        } catch (UnknownHostException ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        }

        lvLog.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //PackageManager pm = getPackageManager();
                Cursor cursor = (Cursor) adapter_log.getItem(position);
                //long time = cursor.getLong(cursor.getColumnIndex("time"));
                final String daddr = cursor.getString(cursor.getColumnIndex("daddr"));
                final int dport = (cursor.isNull(cursor.getColumnIndex("dport")) ? -1 : cursor.getInt(cursor.getColumnIndex("dport")));
                final String saddr = cursor.getString(cursor.getColumnIndex("saddr"));
                final int sport = (cursor.isNull(cursor.getColumnIndex("sport")) ? -1 : cursor.getInt(cursor.getColumnIndex("sport")));
                final int uid = (cursor.isNull(cursor.getColumnIndex("uid")) ? -1 : cursor.getInt(cursor.getColumnIndex("uid")));

                // Get external address
                InetAddress addr = null;
                try {
                    addr = InetAddress.getByName(daddr);
                } catch (UnknownHostException ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }

                String ip;
                int port;
                if (addr.equals(vpn4) || addr.equals(vpn6)) {
                    ip = saddr;
                    port = sport;
                } else {
                    ip = daddr;
                   port = dport;
                }

                //Build popup menu
                PopupMenu popup = new PopupMenu(ActivityMain.this, findViewById(R.id.vwPopupAnchor));

                if (uid >= 0)
                    popup.getMenu().add(Menu.NONE, 1, 1, TextUtils.join(", ", Util.getApplicationNames(uid, ActivityMain.this)));

                //final Intent lookupIP = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.tcpiputils.com/whois-lookup/" + ip));
                popup.getMenu().add(Menu.NONE, 2, 2, getString(R.string.title_log_whois, ip))
                        .setEnabled(true);

               //final Intent lookupPort = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.speedguide.net/port.php?port=" + port));
                if (port > 0)
                    popup.getMenu().add(Menu.NONE, 3, 3, getString(R.string.title_log_port, dport))
                            .setEnabled(true);

             //   popup.getMenu().add(Menu.NONE, 4, 4, SimpleDateFormat.getDateTimeInstance().format(time))
              //          .setEnabled(false);

                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        if (menuItem.getItemId() == 1) {
                            Intent main = new Intent(ActivityMain.this, ActivityMain.class);
                            main.putExtra(ActivityMain.EXTRA_SEARCH, Integer.toString(uid));
                            startActivity(main);
                        } else if (menuItem.getItemId() == 2){}
                            //startActivity(lookupIP);
                        else if (menuItem.getItemId() == 3){}
                            //startActivity(lookupPort);
                        return false;
                    }
                });

                popup.show();
            }
        });























        // Disabled warning
        TextView tvDisabled = (TextView) findViewById(R.id.tvDisabled);
        tvDisabled.setVisibility(enabled ? View.GONE : View.VISIBLE);

        // Application list
        RecyclerView rvApplication = (RecyclerView) findViewById(R.id.rvApplication);
        rvApplication.setHasFixedSize(true);
        rvApplication.setLayoutManager(new LinearLayoutManager(this));
        dh = new DatabaseHelper(this);
        adapter = new RuleAdapter(dh, this);
        rvApplication.setAdapter(adapter);

        // Swipe to refresh
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimary, tv, true);
        swipeRefresh = (SwipeRefreshLayout) findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeColors(Color.WHITE, Color.WHITE, Color.WHITE);
        swipeRefresh.setProgressBackgroundColorSchemeColor(tv.data);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                SinkholeService.reload(null, "pull", ActivityMain.this);
                updateApplicationList(null);
            }
        });

        // Listen for preference changes
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Listen for rule set changes
        IntentFilter iff = new IntentFilter(ACTION_RULES_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(onRulesetChanged, iff);

        // Listen for added/removed applications
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        registerReceiver(packageChangedReceiver, intentFilter);

        // First use
        if (!initialized) {
            // Create view
            LayoutInflater inflater = LayoutInflater.from(this);
            View view = inflater.inflate(R.layout.first, null);
            TextView tvFirst = (TextView) view.findViewById(R.id.tvFirst);
            tvFirst.setMovementMethod(LinkMovementMethod.getInstance());

            // Show dialog
            dialogFirst = new AlertDialog.Builder(this)
                    .setView(view)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (running)
                                prefs.edit().putBoolean("initialized", true).apply();
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (running)
                                finish();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            dialogFirst = null;
                        }
                    })
                    .create();
            dialogFirst.show();
        }

        // Fill application list
        updateApplicationList(getIntent().getStringExtra(EXTRA_SEARCH));

        // Update IAB SKUs
        try {
            new IAB(new IAB.Delegate() {
                @Override
                public void onReady(IAB iab) {
                    try {
                        iab.updatePurchases();

                        if (!IAB.isPurchased(ActivityPro.SKU_LOG, ActivityMain.this))
                            prefs.edit().putBoolean("log", false).apply();
                        if (!IAB.isPurchased(ActivityPro.SKU_THEME, ActivityMain.this)) {
                            if (!"teal".equals(prefs.getString("theme", "teal")))
                                prefs.edit().putString("theme", "teal").apply();
                        }
                        if (!IAB.isPurchased(ActivityPro.SKU_SPEED, ActivityMain.this))
                            prefs.edit().putBoolean("show_stats", false).apply();
                    } catch (Throwable ex) {
                        Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    } finally {
                        iab.unbind();
                    }
                }
            }, this).bind();
        } catch (Throwable ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        }

        // Approve request
        if (getIntent().hasExtra(EXTRA_APPROVE) && !enabled) {
            Log.i(TAG, "Requesting VPN approval");
            swEnabled.toggle();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.i(TAG, "New intent");
        super.onNewIntent(intent);
        if (Build.VERSION.SDK_INT >= MIN_SDK)
            updateApplicationList(intent.getStringExtra(EXTRA_SEARCH));
    }

    @Override
    protected void onResume() {
        float mobileTraffic = (float) (TrafficStats.getMobileRxBytes()+TrafficStats.getMobileTxBytes()) / 1024f / 1024f;
        //tv_mobileTraffic.setText(getString(R.string.msg_mbdaymobileUD, mobileTraffic));

        float wifiRXTraffic = (float) (TrafficStats.getTotalRxBytes()-TrafficStats.getMobileRxBytes());
        float wifiTXTraffic = (float) TrafficStats.getTotalTxBytes()-TrafficStats.getMobileTxBytes();
        float wifiTraffic =  (wifiRXTraffic + wifiTXTraffic) / 1024f / 1024f ;
        //tv_wifiTraffic.setText(getString(R.string.msg_mbdaywifiUD, wifiTraffic));


        dh.addAccessChangedListener(accessChangedListener);

        if (adapter != null)
            adapter.notifyDataSetChanged();
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        dh.removeAccessChangedListener(accessChangedListener);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Destroy");

        if (Build.VERSION.SDK_INT < MIN_SDK) {
            super.onDestroy();
            return;
        }

        running = false;

        dh.close();

        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

        LocalBroadcastManager.getInstance(this).unregisterReceiver(onRulesetChanged);
        unregisterReceiver(packageChangedReceiver);

        if (dialogFirst != null) {
            dialogFirst.dismiss();
            dialogFirst = null;
        }
        if (dialogVpn != null) {
            dialogVpn.dismiss();
            dialogVpn = null;
        }
        if (dialogAbout != null) {
            dialogAbout.dismiss();
            dialogAbout = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        Log.i(TAG, "onActivityResult request=" + requestCode + " result=" + requestCode + " ok=" + (resultCode == RESULT_OK));
        Util.logExtras(data);

        if (requestCode == REQUEST_VPN) {
            // Handle VPN approval
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean("enabled", resultCode == RESULT_OK).apply();
            if (resultCode == RESULT_OK)
                SinkholeService.start("prepared", this);

        } else if (requestCode == REQUEST_INVITE) {
            // Do nothing

        } else if (requestCode == REQUEST_LOGCAT) {
            // Send logcat by e-mail
            if (resultCode == RESULT_OK) {
                Uri target = data.getData();
                if (data.hasExtra("org.openintents.extra.DIR_PATH"))
                    target = Uri.parse(target + "/logcat.txt");
                Log.i(TAG, "Export URI=" + target);
                Util.sendLogcat(target, this);
            }

        } else {
            Log.w(TAG, "Unknown activity result request=" + requestCode);
            super.onActivityResult(requestCode, resultCode, data);
        }


        if (requestCode == REQUEST_PCAP) {
            if (resultCode == RESULT_OK && data != null)
                handleExportPCAP(data);

        } else {
            Log.w(TAG, "Unknown activity result request=" + requestCode);
            //super.onActivityResult(requestCode, resultCode, data);
        }

        if (requestCode == REQUEST_UIDPCAP) {
            if (resultCode == RESULT_OK && data != null)
                handleExportUIDPCAP(data);

        } else {
            Log.w(TAG, "Unknown activity result request=" + requestCode);
            //super.onActivityResult(requestCode, resultCode, data);
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_ROAMING)
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                SinkholeService.reload("other", "permission granted", this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String name) {
        Log.i(TAG, "Preference " + name + "=" + prefs.getAll().get(name));
        if ("enabled".equals(name)) {
            // Get enabled
            boolean enabled = prefs.getBoolean(name, false);

            // Display disabled warning
            TextView tvDisabled = (TextView) findViewById(R.id.tvDisabled);
            tvDisabled.setVisibility(enabled ? View.GONE : View.VISIBLE);

            // Check switch state
            SwitchCompat swEnabled = (SwitchCompat) getSupportActionBar().getCustomView().findViewById(R.id.swEnabled);
            if (swEnabled.isChecked() != enabled)
                swEnabled.setChecked(enabled);

        } else if ("whitelist_wifi".equals(name) ||
                "screen_wifi".equals(name) ||
                "whitelist_other".equals(name) ||
                "screen_other".equals(name) ||
                "whitelist_roaming".equals(name) ||
                "show_user".equals(name) ||
                "show_system".equals(name) ||
                "show_nointernet".equals(name) ||
                "show_disabled".equals(name) ||
                "sort".equals(name) ||
                "imported".equals(name))
            updateApplicationList(null);

        else if ("manage_system".equals(name)) {
            invalidateOptionsMenu();
            updateApplicationList(null);

        } else if ("theme".equals(name) || "dark_theme".equals(name))
            recreate();
    }

    private DatabaseHelper.AccessChangedListener accessChangedListener = new DatabaseHelper.AccessChangedListener() {
        @Override
        public void onChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (adapter != null)
                        adapter.notifyDataSetChanged();
                }
            });
        }
    };

    private BroadcastReceiver onRulesetChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            Util.logExtras(intent);

            if (adapter != null)
                if (intent.hasExtra("connected") && intent.hasExtra("metered"))
                    if (intent.getBooleanExtra("connected", false))
                        if (intent.getBooleanExtra("metered", false))
                            adapter.setMobileActive();
                        else
                            adapter.setWifiActive();
                    else
                        adapter.setDisconnected();
                else
                    updateApplicationList(null);
        }
    };

    private BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            Util.logExtras(intent);
            updateApplicationList(null);
        }
    };

    private void updateApplicationList(final String search) {
        Log.i(TAG, "Update search=" + search);

        new AsyncTask<Object, Object, List<Rule>>() {
            private boolean refreshing = true;

            @Override
            protected void onPreExecute() {
                swipeRefresh.post(new Runnable() {
                    @Override
                    public void run() {
                        if (refreshing)
                            swipeRefresh.setRefreshing(true);
                    }
                });
            }

            @Override
            protected List<Rule> doInBackground(Object... arg) {
                return Rule.getRules(false, TAG, ActivityMain.this);
            }

            @Override
            protected void onPostExecute(List<Rule> result) {
                if (running) {
                    if (adapter != null) {
                        adapter.set(result);

                        SearchView searchView = (SearchView) MenuItemCompat.getActionView(menuSearch);
                        if (search == null) {
                            if (menuSearch != null && menuSearch.isActionViewExpanded())
                                adapter.getFilter().filter(searchView.getQuery().toString());
                        } else {
                            MenuItemCompat.expandActionView(menuSearch);
                            searchView.setQuery(search, true);
                        }
                    }

                    if (swipeRefresh != null) {
                        refreshing = false;
                        swipeRefresh.setRefreshing(false);
                    }
                }
            }
        }.execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (Build.VERSION.SDK_INT < MIN_SDK)
            return false;

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        //inflater.inflate(R.menu.log, menu);


        // Search
        menuSearch = menu.findItem(R.id.menu_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(menuSearch);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (adapter != null)
                    adapter.getFilter().filter(query);
                searchView.clearFocus();
                if (adapter_log != null)
                    adapter_log.getFilter().filter(query);
                searchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (adapter != null)
                    adapter.getFilter().filter(newText);
                //imporant
                if (adapter_log != null)
                    adapter_log.getFilter().filter(newText);

                return true;
            }
        });
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                if (adapter != null)
                    adapter.getFilter().filter(null);
                if (adapter_log != null)
                    adapter_log.getFilter().filter(null);


                return true;
            }
        });

        //if (!Util.hasValidFingerprint(this) || getIntentInvite(this).resolveActivity(getPackageManager()) == null)
           // menu.removeItem(R.id.menu_invite);

       // if (getIntentSupport().resolveActivity(getPackageManager()) == null)
           // menu.removeItem(R.id.menu_support);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        File pcap_file = new File(getCacheDir(), "netguard.pcap");
        boolean export = (getPackageManager().resolveActivity(getIntentPCAPDocument(0), 0) != null);


        File pcap_file_uid = new File(getCacheDir(), "netguarduid.pcap");
        boolean exportuid = (getPackageManager().resolveActivity(getIntentPCAPDocument(1), 0) != null);


        /*if (prefs.getBoolean("manage_system", false)) {
            /*menu.findItem(R.id.menu_app_user).setChecked(prefs.getBoolean("show_user", true));
            menu.findItem(R.id.menu_app_system).setChecked(prefs.getBoolean("show_system", false));

        } else {
           Menu submenu = menu.findItem(R.id.menu_filter).getSubMenu();
            submenu.removeItem(R.id.menu_app_user);
            submenu.removeItem(R.id.menu_app_system);
        }*/

        /*menu.findItem(R.id.menu_app_nointernet).setChecked(prefs.getBoolean("show_nointernet", true));*/
        /*menu.findItem(R.id.menu_app_disabled).setChecked(prefs.getBoolean("show_disabled", true));*/

        String sort = prefs.getString("sort", "name");
        if ("data".equals(sort))
            menu.findItem(R.id.menu_sort_data).setChecked(true);
        else
            menu.findItem(R.id.menu_sort_name).setChecked(true);

        menu.findItem(R.id.menu_pcap_enabled1).setEnabled(prefs.getBoolean("filter", false));
        menu.findItem(R.id.menu_pcap_enabled1).setChecked(prefs.getBoolean("pcap", false));
        menu.findItem(R.id.menu_pcap_export1).setEnabled(pcap_file.exists() && export);
        menu.findItem(R.id.menu_pcap_export2).setEnabled(pcap_file_uid.exists() && exportuid);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "Menu=" + item.getTitle());


        // Handle item selection
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final File pcap_file = new File(getCacheDir(), "netguard.pcap");
        final File pcap_file_udp = new File(getCacheDir(), "netguardudp.pcap");
        final File pcap_file_tcp = new File(getCacheDir(), "netguardtcp.pcap");
        final File pcap_file_ip = new File(getCacheDir(), "netguardip.pcap");
        final File pcap_file_port = new File(getCacheDir(), "netguardport.pcap");
        final File pcap_file_uid = new File(getCacheDir(), "netguarduid.pcap");

        switch (item.getItemId()) {
            /*case R.id.menu_app_user:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("show_user", item.isChecked()).apply();
                return true;

            case R.id.menu_app_system:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("show_system", item.isChecked()).apply();
                return true;*/

            /*case R.id.menu_app_nointernet:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("show_nointernet", item.isChecked()).apply();
                return true;*/

            /*case R.id.menu_app_disabled:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("show_disabled", item.isChecked()).apply();
                return true;*/

            case R.id.menu_sort_name:
                item.setChecked(true);
                prefs.edit().putString("sort", "name").apply();
                prefs.edit().putBoolean("show_user", item.isChecked()).apply();
                prefs.edit().putBoolean("show_system", item.isChecked()).apply();
                return true;

            case R.id.menu_sort_data:
                item.setChecked(true);
                prefs.edit().putString("sort", "data").apply();
                prefs.edit().putBoolean("show_user", item.isChecked()).apply();
                prefs.edit().putBoolean("show_system", item.isChecked()).apply();

                return true;



            case R.id.menu_pcap_enabled1:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("pcap", item.isChecked()).apply();
                SinkholeService.setPcap(item.isChecked() ? pcap_file : null);
                prefs.edit().putBoolean("Uid", item.isChecked()).apply();
                SinkholeService.setPcapuid(item.isChecked() ? pcap_file_uid : null);
                return true;

            case R.id.menu_pcap_export1:
                startActivityForResult(getIntentPCAPDocument(0), REQUEST_PCAP);
                return true;

            case R.id.menu_pcap_export2:
                startActivityForResult(getIntentPCAPDocument(1), REQUEST_UIDPCAP);
                return true;

            case R.id.menu_log_clear1:
                new AsyncTask<Object, Object, Object>() {
                    @Override
                    protected Object doInBackground(Object... objects) {
                        dh_log.clearLog();
                        if (prefs.getBoolean("pcap", false)) {
                            SinkholeService.setPcap(null);
                            if (pcap_file.exists() && !pcap_file.delete())
                                Log.w(TAG, "Delete PCAP failed");
                            SinkholeService.setPcap(pcap_file);
                        } else {
                            if (pcap_file.exists() && !pcap_file.delete())
                                Log.w(TAG, "Delete PCAP failed");
                        }

                        if (prefs.getBoolean("UDP", false)) {
                            SinkholeService.setPcapudp(null);
                            if (pcap_file_udp.exists() && !pcap_file_udp.delete())
                                Log.w(TAG, "Delete PCAP failed");
                            SinkholeService.setPcapudp(pcap_file_udp);
                        }
                        else {
                            if (pcap_file_udp.exists() && !pcap_file_udp.delete())
                                Log.w(TAG, "Delete PCAP failed");
                        }

                        if (prefs.getBoolean("TCP", false)) {
                            SinkholeService.setPcaptcp(null);
                            if (pcap_file_tcp.exists() && !pcap_file_tcp.delete())
                                Log.w(TAG, "Delete PCAP failed");
                            SinkholeService.setPcaptcp(pcap_file_tcp);
                        }
                        else {
                            if (pcap_file_tcp.exists() && !pcap_file_tcp.delete())
                                Log.w(TAG, "Delete PCAP failed");
                        }

                        if (prefs.getBoolean("Ip", false)) {
                            SinkholeService.setPcapip(null);
                            if (pcap_file_ip.exists() && !pcap_file_ip.delete())
                                Log.w(TAG, "Delete PCAP failed");
                            SinkholeService.setPcapip(pcap_file_ip);
                        }
                        else {
                            if (pcap_file_ip.exists() && !pcap_file_ip.delete())
                                Log.w(TAG, "Delete PCAP failed");
                        }

                        if (prefs.getBoolean("Port", false)) {
                            SinkholeService.setPcapport(null);
                            if (pcap_file_port.exists() && !pcap_file_port.delete())
                                Log.w(TAG, "Delete PCAP failed");
                            SinkholeService.setPcapport(pcap_file_port);
                        }
                        else {
                            if (pcap_file_port.exists() && !pcap_file_port.delete())
                                Log.w(TAG, "Delete PCAP failed");
                        }

                        if (prefs.getBoolean("Uid", false)) {
                            SinkholeService.setPcapuid(null);
                            if (pcap_file_uid.exists() && !pcap_file_uid.delete())
                                Log.w(TAG, "Delete PCAP failed");
                            SinkholeService.setPcapuid(pcap_file_uid);
                        }
                        else {
                            if (pcap_file_uid.exists() && !pcap_file_uid.delete())
                                Log.w(TAG, "Delete PCAP failed");
                        }

                        return null;
                    }

                    /*@Override
                    protected void onPostExecute(Object result) {
                        if (running)
                            updateAdapter();
                    }*/
                }.execute();
            return true;









            /*case R.id.menu_app_used:
                item.setChecked(!item.isChecked());

                return true;*/




            case R.id.menu_log:
                startActivity(new Intent(this, ActivityLog.class));
                return true;

            case R.id.menu_settings:
                startActivity(new Intent(this, ActivitySettings.class));
                return true;

            /*case R.id.menu_pro:
                startActivity(new Intent(this, ActivityPro.class));
                return true;*/

            //case R.id.menu_pro1:
                //startActivity(new Intent(this, ActivityPro1.class));
               // return true;

            //case R.id.menu_pro2:
             //   startActivity(new Intent(this, ActivityPro2.class));
             //   return true;

            //case R.id.menu_pro3:
            //    startActivity(new Intent(this, ActivityPro3.class));
             //   return true;


            case R.id.menu_pro4:
                startActivity(new Intent(this, ActivityPro4.class));
                return true;

            case R.id.menu_pro5:
                startActivity(new Intent(this, ActivityPro5.class));
                return true;



            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void menu_about() {
        // Create view
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.about, null);
        //TextView tvVersion = (TextView) view.findViewById(R.id.tvVersion);
        Button btnRate = (Button) view.findViewById(R.id.btnRate);
        TextView tvLicense = (TextView) view.findViewById(R.id.tvLicense);

        // Show version
        //tvVersion.setText(Util.getSelfVersionName(this));
        //if (!Util.hasValidFingerprint(this))
         //   tvVersion.setTextColor(Color.GRAY);

        // Handle license
        tvLicense.setMovementMethod(LinkMovementMethod.getInstance());

        // Handle logcat
        view.setOnClickListener(new View.OnClickListener() {
            private short tap = 0;
            private Toast toast = Toast.makeText(ActivityMain.this, "", Toast.LENGTH_SHORT);

            @Override
            public void onClick(View view) {
                tap++;
                if (tap == 7) {
                    tap = 0;
                    toast.cancel();

                    Intent intent = getIntentLogcat();
                    if (intent.resolveActivity(getPackageManager()) != null)
                        startActivityForResult(intent, REQUEST_LOGCAT);

                } else if (tap > 3) {
                    toast.setText(Integer.toString(7 - tap));
                    toast.show();
                }
            }
        });

        // Handle rate
        btnRate.setVisibility(getIntentRate(this).resolveActivity(getPackageManager()) == null ? View.GONE : View.VISIBLE);
        btnRate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(getIntentRate(ActivityMain.this));
            }
        });

        // Show dialog
        dialogAbout = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        dialogAbout = null;
                    }
                })
                .create();
        dialogAbout.show();
    }

    private static Intent getIntentInvite(Context context) {
        Intent intent = new Intent("com.google.android.gms.appinvite.ACTION_APP_INVITE");
        intent.setPackage("com.google.android.gms");
        intent.putExtra("com.google.android.gms.appinvite.TITLE", context.getString(R.string.menu_invite));
        intent.putExtra("com.google.android.gms.appinvite.MESSAGE", context.getString(R.string.msg_try));
        intent.putExtra("com.google.android.gms.appinvite.BUTTON_TEXT", context.getString(R.string.msg_try));
        // com.google.android.gms.appinvite.DEEP_LINK_URL
        return intent;
    }

    private static Intent getIntentRate(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName()));
        if (intent.resolveActivity(context.getPackageManager()) == null)
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + context.getPackageName()));
        return intent;
    }

    private static Intent getIntentSupport() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://github.com/M66B/NetGuard/blob/master/FAQ.md"));
        return intent;
    }

    private Intent getIntentLogcat() {
        Intent intent;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (Util.isPackageInstalled("org.openintents.filemanager", this)) {
                intent = new Intent("org.openintents.action.PICK_DIRECTORY");
            } else {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=org.openintents.filemanager"));
            }
        } else {
            intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "logcat.txt");
        }
        return intent;
    }


    private void updateAdapter() {
        if (adapter_log != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean udp = prefs.getBoolean("proto_udp", true);
            boolean tcp = prefs.getBoolean("proto_tcp", true);
            boolean other = prefs.getBoolean("proto_other", true);
            boolean allowed = prefs.getBoolean("traffic_allowed", true);
            boolean blocked = prefs.getBoolean("traffic_blocked", true);
            adapter_log.changeCursor(dh_log.getLog(udp, tcp, other, allowed, blocked));
            if (menuSearch != null && menuSearch.isActionViewExpanded()) {
                SearchView searchView = (SearchView) MenuItemCompat.getActionView(menuSearch);
                adapter_log.getFilter().filter(searchView.getQuery().toString());
            }
        }
    }




    private Intent getIntentPCAPDocument(int pro) {
        Intent intent;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (Util.isPackageInstalled("org.openintents.filemanager", this)) {
                intent = new Intent("org.openintents.action.PICK_DIRECTORY");
            } else {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=org.openintents.filemanager"));
            }
        } else if (pro==0){
            intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE, "netguard_" + new SimpleDateFormat("yyyyMMdd").format(new Date().getTime()) + ".pcap");
        } else{
            intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE, "netguarduid_" + new SimpleDateFormat("yyyyMMdd").format(new Date().getTime()) + ".pcap");

        }
        return intent;
    }

    private void handleExportPCAP(final Intent data) {
        new AsyncTask<Object, Object, Throwable>() {
            @Override
            protected Throwable doInBackground(Object... objects) {
                OutputStream out = null;
                FileInputStream in = null;
                try {
                    // Stop capture
                    SinkholeService.setPcap(null);

                    Uri target = data.getData();
                    if (data.hasExtra("org.openintents.extra.DIR_PATH"))
                        target = Uri.parse(target + "/netguard.pcap");
                    Log.i(TAG, "Export PCAP URI=" + target);
                    out = getContentResolver().openOutputStream(target);

                    File pcap = new File(getCacheDir(), "netguard.pcap");
                    in = new FileInputStream(pcap);

                    int len;
                    long total = 0;
                    byte[] buf = new byte[4096];
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                        total += len;
                    }
                    Log.i(TAG, "Copied bytes=" + total);

                    return null;
                } catch (Throwable ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    Util.sendCrashReport(ex, ActivityMain.this);
                    return ex;
                } finally {
                    if (out != null)
                        try {
                            out.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                    if (in != null)
                        try {
                            in.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }

                    // Resume capture
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ActivityMain.this);
                    if (prefs.getBoolean("pcap", false)) {
                        File pcap_file = new File(getCacheDir(), "netguard.pcap");
                        SinkholeService.setPcap(pcap_file);
                    }
                }
            }

            @Override
            protected void onPostExecute(Throwable ex) {
                if (ex == null)
                    Toast.makeText(ActivityMain.this, R.string.msg_completed, Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(ActivityMain.this, ex.toString(), Toast.LENGTH_LONG).show();
            }
        }.execute();
    }


    private void handleExportUIDPCAP(final Intent data) {
        new AsyncTask<Object, Object, Throwable>() {
            @Override
            protected Throwable doInBackground(Object... objects) {
                OutputStream out = null;
                FileInputStream in = null;
                try {
                    // Stop capture
                    SinkholeService.setPcapuid(null);

                    Uri target = data.getData();
                    if (data.hasExtra("org.openintents.extra.DIR_PATH"))
                        target = Uri.parse(target + "/netguarduid.pcap");
                    Log.i(TAG, "Export PCAP URI=" + target);
                    out = getContentResolver().openOutputStream(target);

                    File pcapuid = new File(getCacheDir(), "netguarduid.pcap");
                    in = new FileInputStream(pcapuid);

                    int len;
                    long total = 0;
                    byte[] bufuid = new byte[4096];
                    while ((len = in.read(bufuid)) > 0) {
                        out.write(bufuid, 0, len);
                        total += len;
                    }
                    Log.i(TAG, "Copied bytes=" + total);

                    return null;
                } catch (Throwable ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    Util.sendCrashReport(ex, ActivityMain.this);
                    return ex;
                } finally {
                    if (out != null)
                        try {
                            out.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                    if (in != null)
                        try {
                            in.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }

                    // Resume capture
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ActivityMain.this);
                    if (prefs.getBoolean("Uid", false)) {
                        File pcap_file_uid = new File(getCacheDir(), "netguarduid.pcap");
                        SinkholeService.setPcapuid(pcap_file_uid);
                    }
                }
            }

            @Override
            protected void onPostExecute(Throwable ex) {
                if (ex == null)
                    Toast.makeText(ActivityMain.this, R.string.msg_completed, Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(ActivityMain.this, ex.toString(), Toast.LENGTH_LONG).show();
            }
        }.execute();
    }


    static int switch_number=2;

    void Input_switchon(int switchon){

        switch_number = switchon;
    }

    int Return_switchon(){

        return switch_number;
    }


}