package kvj.taskw.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.ProgressBar;

import org.json.JSONObject;
import org.kvj.bravo7.form.FormController;
import org.kvj.bravo7.form.impl.bundle.StringBundleAdapter;
import org.kvj.bravo7.form.impl.widget.TransientAdapter;
import org.kvj.bravo7.log.Logger;
import org.kvj.bravo7.util.Tasks;

import java.util.Map;

import kvj.taskw.App;
import kvj.taskw.R;
import kvj.taskw.data.AccountController;
import kvj.taskw.data.Controller;

public class MainActivity extends AppCompatActivity {

    Logger logger = Logger.forInstance(this);

    Controller controller = App.controller();
    private Toolbar toolbar = null;
    private DrawerLayout navigationDrawer = null;
    private NavigationView navigation = null;

    private FormController form = new FormController(null);
    private MainList list = null;
    private Runnable updateTitleAction = new Runnable() {
        @Override
        public void run() {
            if (null != toolbar) toolbar.setSubtitle(list.reportInfo().description);
        }
    };
    private FloatingActionButton addButton = null;
    private ProgressBar progressBar = null;
    private AccountController.TaskListener progressListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        toolbar = (Toolbar) findViewById(R.id.list_toolbar);
        navigationDrawer = (DrawerLayout) findViewById(R.id.list_navigation_drawer);
        navigation = (NavigationView) findViewById(R.id.list_navigation);
        list = (MainList) getSupportFragmentManager().findFragmentById(R.id.list_list_fragment);
        addButton = (FloatingActionButton) findViewById(R.id.list_add_btn);
        progressBar = (ProgressBar) findViewById(R.id.list_progress);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(getDrawerToggleDelegate().getThemeUpIndicator());
        list.listener(new MainListAdapter.ItemListener() {
            @Override
            public void onEdit(JSONObject json) {
                // Start editor
                edit(json);
            }
        });
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add();
            }
        });
        progressListener = MainActivity.setupProgressListener(this, progressBar);
        form.add(new TransientAdapter<>(new StringBundleAdapter(), null), App.KEY_ACCOUNT);
        form.add(new TransientAdapter<>(new StringBundleAdapter(), null), App.KEY_REPORT);
        form.add(new TransientAdapter<>(new StringBundleAdapter(), null), App.KEY_QUERY);
        form.load(this, savedInstanceState);
    }

    private static AccountController.TaskListener setupProgressListener(final Activity activity, final View bar) {
        return new AccountController.TaskListener() {
            @Override
            public void onStart() {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bar.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onFinish() {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bar.setVisibility(View.GONE);
                    }
                });
            }
        };
    }

    private void add() {
        Intent intent = new Intent(this, EditorActivity.class);
        final String account = form.getValue(App.KEY_ACCOUNT);
        controller.accountController(account).intentForEditor(intent, null);
        startActivityForResult(intent, RESULT_OK);
    }

    private void edit(JSONObject json) {
        Intent intent = new Intent(this, EditorActivity.class);
        final String account = form.getValue(App.KEY_ACCOUNT);
        if (controller.accountController(account).intentForEditor(intent, json.optString("uuid"))) { //
            startActivityForResult(intent, RESULT_OK);
        } else {
            controller.messageShort("Invalid task");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        form.save(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        addButton.setEnabled(false);
        if (checkAccount()) {
            addButton.setEnabled(true);
            accountController().listeners().add(progressListener);
        }
    }

    @Override
    protected void onDestroy() {
        if (null != accountController()) {
            accountController().listeners().remove(progressListener);
        }
        super.onDestroy();
    }

    private AccountController accountController() {
        return controller.accountController(form.getValue(App.KEY_ACCOUNT, String.class));
    }

    private boolean checkAccount() {
        if (null != form.getValue(App.KEY_ACCOUNT)) { // Have account
            return true;
        }
        String account = controller.currentAccount();
        if (account == null) {
            // Start new account UI
            controller.addAccount(this);
            return false;
        } else {
            logger.d("Refresh account:", account);
            form.setValue(App.KEY_ACCOUNT, account);
            refreshReports();
        }
        return true;
    }

    private void refreshReports() {
        final String account = form.getValue(App.KEY_ACCOUNT);
        new Tasks.ActivitySimpleTask<Map<String, String>>(this){

            @Override
            protected Map<String, String> doInBackground() {
                return controller.accountController(account).taskReports();
            }

            @Override
            public void finish(Map<String, String> result) {
                // We're in UI thread
                MenuItem reportsMenu = navigation.getMenu().findItem(R.id.menu_nav_reports);
                reportsMenu.getSubMenu().clear();
                for (Map.Entry<String, String> entry : result.entrySet()) { // Add reports
                    addReportMenuItem(entry.getKey(), entry.getValue(), reportsMenu.getSubMenu());
                }
                if (null == form.getValue(App.KEY_QUERY)) {
                    // Report mode
                    String report = form.getValue(App.KEY_REPORT);
                    if (null == report || !result.containsKey(report)) {
//                        report = result.keySet().iterator().next(); // First item
                        report = "next";
                    }
                    form.setValue(App.KEY_REPORT, report);
                }
                list.load(form, updateTitleAction);
            }

            private void addReportMenuItem(final String key, String title, SubMenu menu) {
                menu.add(title).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        // Show report
                        form.setValue(App.KEY_REPORT, key);
                        form.setValue(App.KEY_QUERY, null);
                        list.load(form, updateTitleAction);
                        return false;
                    }
                });
            }
        }.exec();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_tb_reload:
                list.reload();
                break;
            case R.id.menu_tb_sync:
                sync();
                break;
        }
        return true;
    }

    private void sync() {
        final String account = form.getValue(App.KEY_ACCOUNT);
        if (null != account) { // Do sync
            new Tasks.ActivitySimpleTask<String>(this){

                @Override
                protected String doInBackground() {
                    return controller.accountController(account).callSync();
                }

                @Override
                public void finish(String result) {
                    if (null != result) { // Error
                        controller.messageLong(result);
                    } else {
                        controller.messageShort("Sync success");
                        list.reload();
                    }
                }
            }.exec();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        list.reload(); // Reload after edit
    }
}
