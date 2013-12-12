package com.example.weather;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.support.v4.view.ViewPager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: slavian
 * Date: 25.11.13
 * Time: 23:49
 * To change this template use File | Settings | File Templates.
 */
public class Main extends Activity implements DbConstants {

    private static final String TAG = "FUCKENFUCK::Main ";

    AlarmManager alarmManager;
    PendingIntent pendingIntent;
    MyBroadcastReceiver myBroadcastReceiver;

    //ProgressBar progressBar;
    ImageButton add;

    // local storage
    List<City> cities;
    List<Current> now;
    List<Forecast> forecast;

    // saved
    private static final String KEY_SAVED = "currentCity.saved";
    SharedPreferences sPref;
    public static final String KEY_GRAD = "°";

    DbAdapter mDbHelper;


    private static final int DELETE_ID = Menu.FIRST;

    //now
    TextView current;
    TextView temp;
    TextView wind;
    TextView state;
    TextView time;
    ImageView image;

    //cities
    AutoCompleteTextView autoCompleteTextView;
    List<City> found;
    //ArrayAdapter<String> cityAdapter;
    ListView list;
    //SimpleCursorAdapter ad;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        setContentView(R.layout.landscape);

        mDbHelper = new DbAdapter(this);

        if (getCurrent() == -1)
            insertDefaultCities();

        current = (TextView) findViewById(R.id.current);

        add = (ImageButton) findViewById(R.id.add);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                add.setVisibility(View.INVISIBLE);
                //progressBar.setVisibility(View.VISIBLE);
                Toast.makeText(Main.this, "update", 1000).show();

                Intent intent = new Intent(Main.this, UpdateService.class);
                try {
                    startService(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });


        // create local storage
        cities = new ArrayList<City>();
        now = new ArrayList<Current>();
        forecast = new ArrayList<Forecast>();

        upAllData();
        upAllUI();

        myBroadcastReceiver = new MyBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter(UpdateService.ACTION_UP);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        registerReceiver(myBroadcastReceiver, intentFilter);
        start();

        Intent myIntent = new Intent(Main.this, MyAlarmBroadcastReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(Main.this, 0, myIntent, 0);

        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.currentThreadTimeMillis(), AlarmManager.INTERVAL_HOUR, pendingIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            mDbHelper.close();
        } catch (Exception e) {
        }
        unregisterReceiver(myBroadcastReceiver);
        alarmManager.cancel(pendingIntent);
    }



    private int getCurrent() {
        sPref = getPreferences(MODE_PRIVATE);
        String savedText = sPref.getString(KEY_SAVED, "-1");
        return (int) Integer.parseInt(savedText);
    }

    private void setCurrent(int id) {

        sPref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor ed = sPref.edit();
        ed.putString(KEY_SAVED, new String(new Integer(id).toString()));
        ed.commit();
        // надо бы переставить метку
        updateCurrentLabel();
    }
//    private void clearCurrent()
//    {
//        sPref = getPreferences(MODE_PRIVATE);
//        SharedPreferences.Editor ed = sPref.edit();
//        ed.clear();
//        ed.commit();
//    }


    private void updateCurrentLabel() {

        try {
            current.setText(cities.get(getCurrent()).name);
        } catch (Exception e) {
            Log.w(TAG, "unable to update Label " + e.getMessage());
        }
    }

    public void start() {
        Intent intent = new Intent(Main.this, UpdateService.class);
        startService(intent);
        //startProgressBar();
    }

    public void upAllData() {
        Cursor cursor = null;
        try {
            mDbHelper.open();
            cursor = mDbHelper.fetchCity();
            if (cursor != null && (cities.size() == 0 || cursor.getCount() > 0))
                cities.clear();
            else {
                Log.w(TAG, "unable to update local storage. troubles with DB");
                Toast.makeText(Main.this, "unable", 1000);
                return;
            }

            while (cursor.moveToNext()) {
                cities.add(new City(cursor.getString(cursor.getColumnIndex(KEY_NAME)),
                        cursor.getString(cursor.getColumnIndex(KEY_LAT)),
                        cursor.getString(cursor.getColumnIndex(KEY_LONG))));
            }

            cursor = null;
            cursor = mDbHelper.fetchCurrent();
            if (cursor != null && (now.size() == 0 || cursor.getCount() > 0))
                now.clear();
            else {
                Log.w(TAG, "unable to update local storage. troubles with DB");
                Toast.makeText(Main.this, "unable", 1000);
                return;
            }

            while (cursor.moveToNext()) {
                now.add(new Current(cursor.getString(cursor.getColumnIndex(KEY_TEMPC)),
                        cursor.getString(cursor.getColumnIndex(KEY_WSK)),
                        cursor.getString(cursor.getColumnIndex(KEY_WTHDESC)),
                        cursor.getString(cursor.getColumnIndex(KEY_OBSTIME)),
                        cursor.getInt(cursor.getColumnIndex(KEY_WTHCODE))));
            }

            cursor = null;
            String s = cities.get(getCurrent()).name;
            cursor = mDbHelper.fetchForecast(s);
            if (cursor != null && (forecast.size() == 0 || cursor.getCount() > 0))
                forecast.clear();
            else {
                Log.w(TAG, "unable to update local storage. troubles with DB");
                Toast.makeText(Main.this, "unable", 1000);
                return;
            }

            while (cursor.moveToNext()) {
                forecast.add(new Forecast(cursor.getString(cursor.getColumnIndex(KEY_TEMPMINC)),
                        cursor.getString(cursor.getColumnIndex(KEY_TEMPMAXC)),
                        cursor.getString(cursor.getColumnIndex(KEY_WSK)),
                        cursor.getString(cursor.getColumnIndex(KEY_WTHDESC)),
                        cursor.getString(cursor.getColumnIndex(KEY_DATE)),
                        cursor.getInt(cursor.getColumnIndex(KEY_WTHCODE))));

            }
            cursor.close();

            mDbHelper.close();
        } catch (Exception e) {
            Log.w(TAG, "whole crash in big try-catch block  " + e.getMessage());
            if (cursor != null) cursor.close();
            mDbHelper.close();
        }
    }

    public void upAllUI() {

        state = (TextView) findViewById(R.id.state);
        wind = (TextView) findViewById(R.id.wind);
        temp = (TextView) findViewById(R.id.temp);
        current = (TextView) findViewById(R.id.current);
        time = (TextView) findViewById(R.id.time);
        image = (ImageView) findViewById(R.id.imageView);
        try {
            temp.setText(now.get(getCurrent()).tempC + " " + KEY_GRAD);
            time.setText(now.get(getCurrent()).observed);
            wind.setText(now.get(getCurrent()).wind+" km/h");
            state.setText(now.get(getCurrent()).descr);
            image.setImageResource(now.get(getCurrent()).image);
            updateCurrentLabel();
            fillCities();
        } catch (Exception e) {
            Log.w(TAG, "can't load main page!!!" + e.getMessage());
        }


        add.setVisibility(View.VISIBLE);

        ListView lv = (ListView) findViewById(R.id.forecast);
        ForecastAdapter customAdapter = new ForecastAdapter(this, R.layout.forecast_list_item, forecast);
        lv.setAdapter(customAdapter);
    }

//    public void stopProgressBar() {
//        progressBar.setVisibility(View.INVISIBLE);
//        imb.setVisibility(View.VISIBLE);
//    }
//
//    private void startProgressBar() {
//        progressBar.setVisibility(View.VISIBLE);
//        imb.setVisibility(View.INVISIBLE);
//    }

    private void insertDefaultCities() {
        mDbHelper.open();
        mDbHelper.dropCities();
        HashMap<String, String> h = new HashMap<String, String>();
        h.put(KEY_NAME, "London, UK");
        h.put(KEY_LAT, "51.517");
        h.put(KEY_LONG, "-0.106");
        mDbHelper.createCity(h);

        h.put(KEY_NAME, "Moscow, Russia");
        h.put(KEY_LAT, "55.752");
        h.put(KEY_LONG, "37.616");
        mDbHelper.createCity(h);

        h.put(KEY_NAME, "Saint Petersburg, Russia");
        h.put(KEY_LAT, "59.894");
        h.put(KEY_LONG, "30.264");
        mDbHelper.createCity(h);
        mDbHelper.close();

        setCurrent(0);
    }

    private void fillCities() {

        list = (ListView) findViewById(R.id.listViewCities);

        List<String> data = new ArrayList<String>();
        for (int i = 0; i < cities.size(); ++i) {
            data.add(cities.get(i).name);
        }

        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, R.layout.city_item, R.id.city_item_text, data);
        list.setAdapter(ad);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                setCurrent(i);
                upAllData();
                upAllUI();
                //Toast.makeText(Main.this, "CURRENT!!!!!!    -   " + new Integer(i).toString(), 3000).show();
            }
        });

        registerForContextMenu(list);


        found = new ArrayList<City>();
        autoCompleteTextView = (AutoCompleteTextView) findViewById(R.id.editText);
        autoCompleteTextView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (KeyEvent.ACTION_DOWN == keyEvent.getAction() && i == KeyEvent.KEYCODE_ENTER) {
                    String s = autoCompleteTextView.getText().toString();
                    //startProgressBar();
                    CityAsyncTask task = new CityAsyncTask();
                    task.execute(new String[]{s});
                }
                return true;
            }
        });
    }

    private class CityAsyncTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... parameter) {
            CityGeocoder cgc = new CityGeocoder();
            found = cgc.getCities(parameter[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void res) {

            if (found.size() > 0) {

                if (commitCity(found.get(0))) {
                    cities.add(found.get(0));
                    setCurrent(cities.size() - 1);
                    start();
                }
            }

            //stopProgressBar();
            autoCompleteTextView.setText("");
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, DELETE_ID, 0, "delete");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case DELETE_ID:
                if (cities.size() == 1) {
                    Toast.makeText(this, "can't delete the last city!", 1000).show();
                    return true;
                }
                AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
                String name = cities.get((int) info.id).name;
                cities.remove(info.id);
                now.remove(info.id);
                forecast.remove(info.id);
                removeFromBase(name);

                if (getCurrent() == (int) info.id) {
                    setCurrent(0);
                } else if (getCurrent() != 0 && getCurrent() >= info.id) {
                    setCurrent(getCurrent() - 1);
                }
                upAllUI();
                start();

                return true;
        }
        return super.onContextItemSelected(item);
    }

    private boolean commitCity(City c) {
        mDbHelper.open();
        HashMap<String, String> h = new HashMap<String, String>();
        h.put(KEY_NAME, c.name);
        h.put(KEY_LAT, c.latitude);
        h.put(KEY_LONG, c.longitude);
        boolean b = mDbHelper.createCity(h);
        mDbHelper.close();
        return b;
    }

    private void removeFromBase(String name) {
        mDbHelper.open();
        int id = (int) mDbHelper.getCityId(name);
        mDbHelper.deleteCity((long) id);
        mDbHelper.deleteCurrent((long) id);
        mDbHelper.deleteForecast((long) id);
        mDbHelper.close();
    }

}
