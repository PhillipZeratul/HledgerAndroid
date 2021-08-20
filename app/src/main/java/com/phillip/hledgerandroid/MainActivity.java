package com.phillip.hledgerandroid;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener {

    private static final int INTENT_LOAD_ACCOUNTS = 2;
    private static final String TAG = "HledgerAndroid";

    private ArrayList<String> accounts = new ArrayList<>();
    private float totalExpenses;
    private float totalRevenues;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        loadDefaultAccounts();
        showTransactions();
    }

    public void startAddTransactionActivity(View view) {
        Intent intent = new Intent(this, AddTransactionActivity.class);
        startActivity(intent);
    }

    public void showSettingsPopup(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.setOnMenuItemClickListener(this);
        popup.inflate(R.menu.settings);
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_load_accounts:
                actionLoadAccounts();
                return true;
            case R.id.action_save_csv:
                actionSaveCsv();
                return true;
            default:
                return false;
        }
    }

    private void actionLoadAccounts() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, INTENT_LOAD_ACCOUNTS);
    }

    private void actionSaveCsv() {
        Intent resultIntent = new Intent(Intent.ACTION_SEND);

        File privateRootDir = getFilesDir();
        String journalFilePath = privateRootDir.getAbsolutePath() + "/" +
                ((HledgerAndroid)this.getApplication()).getJournalFileName();
        File requestFile = new File(journalFilePath);

        try {
            Uri fileUri = FileProvider.getUriForFile(this,
                    "com.phillip.hledgerandroid.fileprovider",
                    requestFile);
            if (fileUri != null) {
                // Grant temporary read permission to the content URI
                resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                resultIntent.setType("text/plain");
                resultIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                startActivity(resultIntent);
            } else {
                CharSequence text = ((HledgerAndroid)this.getApplication()).getJournalFileName() + "do not exsist!";
                showToast(text);
            }
        } catch (IllegalArgumentException e) {
            Log.e("File Selector",
                    "The selected file can't be shared: " + requestFile.toString());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case INTENT_LOAD_ACCOUNTS:
                loadNewAccounts(resultData);
                break;
            default:
                break;
        }
    }

    private void loadDefaultAccounts() {
        try {
            String accountsFileName = ((HledgerAndroid)this.getApplication()).getAccountsFileName();
            FileInputStream in = openFileInput(accountsFileName);
            parseAccounts(in);
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadNewAccounts(Intent resultData)
    {
        Uri uri = null;
        if (resultData != null) {
            uri = resultData.getData();
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                parseAccounts(in);
                in.close();
                in = getContentResolver().openInputStream(uri);
                saveAccounts(in);
                in.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void parseAccounts(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        for (String line; (line = r.readLine()) != null; ) {
            if (line.startsWith(";"))
                continue;
            else if (line.startsWith("account ")) {
                String[] splitted = line.split("account |;|  ");
                //Log.v(TAG, "+" + splitted[1] + "+");
                accounts.add(splitted[1]);
            }
        }
        // Save accounts to global variable.
        ((HledgerAndroid)this.getApplication()).setAccounts(accounts);
    }

    private void saveAccounts(InputStream in) throws IOException {
        String accountsFileName = ((HledgerAndroid)this.getApplication()).getAccountsFileName();
        FileOutputStream out = openFileOutput(accountsFileName, Context.MODE_PRIVATE);
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.close();
    }

    // TODO: Show transactions in main screen.
    private void showTransactions() {
        String journalFileName = ((HledgerAndroid)this.getApplication()).getJournalFileName();
        try {
            FileInputStream in = openFileInput(journalFileName);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            ArrayList<String> recyclerDatas = new ArrayList<String>();
            String[] datas = new String[0];

            for (String line; (line = r.readLine()) != null; ) {
                if (line.matches("(\\d{4}/\\d{2}/\\d{2}).*")) {
                    // 2021/08/05.*
                    String date = "", description = "", account = "", number = "", account2 = "", number2 = "";
                    String[] splitted = line.split(" ", 2); // splitted[0]: date, splitted[1]: description
                    date = splitted[0];
                    if (splitted.length > 1) {
                        description = splitted[1];
                    }
                    line = r.readLine();
                    splitted = line.split("  ");
                    for (int i = 0; i < splitted.length; i++) {
                        if (splitted[i].matches("(?!^-?\\d+\\.?\\d+$)^.+$")) {
                            account = splitted[i];
                        } else if (splitted[i].matches("^-?\\d+\\.?\\d+$")) {
                            number = splitted[i];
                        }
                    }
                    line = r.readLine();
                    splitted = line.split("  ");
                    for (int i = 0; i < splitted.length; i++) {
                        if (splitted[i].matches("(?!^-?\\d+\\.?\\d+$)^.+$")) {
                            // not 20 / -20.65
                            account2 = splitted[i];
                        } else if (splitted[i].matches("^-?\\d+\\.?\\d+$")) {
                            number2 = splitted[i];
                        }
                    }
                    //Log.v(TAG, "Date: " + date + " Description: " + description + " Account: " + account + " Number: " + number + " Account2: " + account2 + " Number2: " + number2);
                    recyclerDatas.add("Date: " + date + " Description: " + description + " Account: " + account + " Number: " + number + " Account2: " + account2 + " Number2: " + number2);
                }
            }
            in.close();
            datas = recyclerDatas.toArray(datas);
            setupRecycler(datas);
        } catch (FileNotFoundException e) {
            CharSequence text = ((HledgerAndroid)this.getApplication()).getJournalFileName() + "not found.";
            showToast(text);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupRecycler(String[] datas) {
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        CustomRecyclerAdapter adapter = new CustomRecyclerAdapter(datas);
        recyclerView.setAdapter(adapter);
    }

    private void showToast(CharSequence text) {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    // TODO: Finish text_row_item.xml
}