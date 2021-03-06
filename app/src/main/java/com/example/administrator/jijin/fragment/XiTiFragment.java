package com.example.administrator.jijin.fragment;
//保存考试类型，体验数据库，正式数据库，错题数据库

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.administrator.jijin.Config.Config;
import com.example.administrator.jijin.MainActivity;
import com.example.administrator.jijin.R;
import com.example.administrator.jijin.activity.ChapterActivity;
import com.example.administrator.jijin.activity.CreateActivity;
import com.example.administrator.jijin.activity.WebViewActivity;
import com.example.administrator.jijin.adapter.XiTiListAdapter;
import com.example.administrator.jijin.adapter.XiTiVpadapter;
import com.example.administrator.jijin.bean.Banner;
import com.example.administrator.jijin.bean.ExamSmallItem;
import com.example.administrator.jijin.util.ConfigUtil;
import com.example.administrator.jijin.util.JsonUtil;
import com.example.administrator.jijin.util.SQLiteUtil;
import com.example.administrator.jijin.view.MyListView;
import com.squareup.picasso.Picasso;
import com.umeng.analytics.MobclickAgent;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by Administrator on 2016/4/26.
 */
public class XiTiFragment extends Fragment implements AdapterView.OnItemClickListener,MainActivity.MainListener{
    private MainActivity mainActivity;
    private View view;
    private MyListView lv;
    private XiTiListAdapter adapter;
    private ScheduledExecutorService scheduledExecutorService;
    private int currentItem = 0;
    private List<ImageView> imageViews = new ArrayList<>();
    private List<Banner> banners;
    private List<ExamSmallItem> examSmallItems = new ArrayList<>();
    private XiTiVpadapter vpAdapter;
    private SharedPreferences sp;
    private String number;
    private SQLiteDatabase examSqLite, xiSqLite,normalSqLite, cuoSqLite, saveSqLite;
    private RequestQueue mQueue;
    private InputStream inputStream;
    private URLConnection connection;
    private OutputStream outputStream;
    private Dialog dialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
        mQueue = Volley.newRequestQueue(mainActivity);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.from(mainActivity).inflate(R.layout.fragment_xiti, null);
        initView();
        initData();
        initListener();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        MobclickAgent.onPageStart("XiTiFragment");
    }

    @Override
    public void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd("XiTiFragment");
    }

    private void initListener() {
        lv.setOnItemClickListener(this);
    }

    private void initData() {
        mainActivity.setMainListener(this);
        vpAdapter = new XiTiVpadapter(imageViews);
        adapter = new XiTiListAdapter(examSmallItems, mainActivity);
        lv.setAdapter(adapter);
        sp = mainActivity.getSharedPreferences(ConfigUtil.spSave, Activity.MODE_PRIVATE);
        //创建考试类型和收藏数据库，下载体验答题数据库
        examSqLite = SQLiteDatabase.openOrCreateDatabase(ConfigUtil.examTypeFileName, ConfigUtil.mi_ma, null);
        examSqLite.execSQL("create table if not exists exam(id integer,formalDBURL varchar,formalDBSize varchar,title varchar)");
        saveSqLite = SQLiteDatabase.openOrCreateDatabase(ConfigUtil.saveFilename, ConfigUtil.mi_ma, null);
        saveSqLite.execSQL("create table if not exists saveData" + "("
                + "id INTEGER DEFAULT '1' NOT NULL PRIMARY KEY AUTOINCREMENT,"
                + "题干 varchar,"
                + "A varchar,"
                + "B varchar,"
                + "C varchar,"
                + "D varchar,"
                + "E varchar,"
                + "答案 varchar,"
                + "解析 varchar)");
        getDataFromNet();
    }

    private void getDataFromNet() {
        //轮播图
        StringRequest request = new StringRequest(Config.HOME,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        if (response.contains("ï»¿")){
                            response=response.replace("ï»¿","");
                        }
                        banners = JsonUtil.parseJsonBanner(response);
                        for (int i = 0; i < banners.size(); i++) {
                            ImageView iv = new ImageView(mainActivity);
                            iv.setScaleType(ImageView.ScaleType.FIT_XY);
                            iv.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    Intent intent = new Intent(mainActivity, WebViewActivity.class);
                                    intent.putExtra("title", banners.get(currentItem).getTitle());
                                    intent.putExtra("url", banners.get(currentItem).getContentURL());
                                    mainActivity.startActivity(intent);
                                }
                            });
                            Picasso.with(mainActivity).load(banners.get(i).getImageURL()).into(iv);
                            imageViews.add(iv);
                        }
                        vpAdapter.setData(imageViews);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("TAG", error.getMessage(), error);
            }
        });
        mQueue.add(request);

        //考试类型
        examSmallItems = SQLiteUtil.getExamTableData(examSqLite, "exam");
        if (examSmallItems.size() == 0) {
            StringRequest stringRequest = new StringRequest(Config.TYPE,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            examSmallItems = JsonUtil.parseJsonExam(response);
                            for (int i = 0; i < examSmallItems.size(); i++) {
                                ContentValues values = new ContentValues();
                                values.put("formalDBURL", examSmallItems.get(i).getFormalDBURL());
                                values.put("title", examSmallItems.get(i).getTitle());
                                examSqLite.insert("exam", "id", values);
                            }
                            examSqLite.close();
                            adapter.setData(examSmallItems);
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("TAG", error.getMessage(), error);
                }
            });
            mQueue.add(stringRequest);
        } else {
            adapter.setData(examSmallItems);
        }
        setNumber();
    }

    private void initView() {
        lv = (MyListView) view.findViewById(R.id.lv);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
        Intent intent = new Intent(mainActivity, ChapterActivity.class);
        intent.putExtra("chapter", examSmallItems.get(position).getTitle());
        intent.putExtra("position", position);
        intent.putExtra("isXiTi", true);
        number = sp.getString("number", "");
        if (number != "") {
            if (sp.getBoolean(position + "", true)) {
                final String urlString = examSmallItems.get(position).getFormalDBURL();
                if (!urlString.contains(".db")) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(mainActivity);
                    builder.setMessage("客官，后台数据更新中，完成后第一时间推送给您。");
                    builder.setTitle("提示");
                    builder.setPositiveButton("我知道了", null);
                    builder.create().show();
                } else {
                    dialog = new AlertDialog.Builder(mainActivity).setTitle("加载中...").
                            setView(new ProgressBar(mainActivity)).setCancelable(false).show();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            downSqLite(urlString, position);
                        }
                    }).start();
                }
            } else {
                startActivity(intent);
            }
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(mainActivity);
            builder.setMessage("你还没有登录，确定现在登录？");
            builder.setTitle("提示");
            builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intentLogin = new Intent(mainActivity, CreateActivity.class);
                    startActivity(intentLogin);
                }
            });
            builder.setNegativeButton("取消", null);
            builder.create().show();
        }
    }

    private Handler handlerDown = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 2://下载完成
                    dialog.dismiss();
                    Toast.makeText(mainActivity, "加载完成", Toast.LENGTH_LONG).show();
                    break;
                default:
                    break;
            }
        }
    };

    //下载数据库
    private void downSqLite(String urlString, int position) {
        try {
            URL url = new URL(urlString);
            connection = url.openConnection();
            if (connection.getReadTimeout() == 5) {
                Toast.makeText(mainActivity, "网络连接超时,请重试", Toast.LENGTH_SHORT).show();
                return;
            }
            inputStream = connection.getInputStream();
            outputStream = mainActivity.openFileOutput(ConfigUtil.getNormalSqLite(position), Context.MODE_PRIVATE);
            byte[] buffer = new byte[200];
            //开始读取
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            normalSqLite = SQLiteDatabase.openOrCreateDatabase(ConfigUtil.path + ConfigUtil.getNormalSqLite(position), ConfigUtil.mi_ma, null);
            cuoSqLite = SQLiteDatabase.openOrCreateDatabase(ConfigUtil.path + ConfigUtil.getCuoSqLite(position), ConfigUtil.mi_ma, null);
            xiSqLite = SQLiteDatabase.openOrCreateDatabase(ConfigUtil.path + ConfigUtil.getXiSqLite(position), ConfigUtil.mi_ma, null);
            SQLiteUtil.creatTable(normalSqLite, cuoSqLite);
            SQLiteUtil.creatXiTiTable(normalSqLite,xiSqLite);
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean(position + "", false);
            editor.commit();
            Message message2 = new Message();
            message2.what = 2;
            handlerDown.sendMessage(message2);
        } catch (MalformedURLException e1) {
            e1.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (examSqLite != null) {
            examSqLite.close();
        }
        if (normalSqLite != null) {
            normalSqLite.close();
        }
        if (cuoSqLite != null) {
            cuoSqLite.close();
        }
        if (saveSqLite != null) {
            saveSqLite.close();
        }
    }

    @Override
    public void xiTiRestart() {
        setNumber();
    }

    private void setNumber(){
        for (int i = 0; i < examSmallItems.size(); i++) {
            if (!sp.getBoolean(i + "", true)) {
                xiSqLite = SQLiteDatabase.openOrCreateDatabase(ConfigUtil.path + ConfigUtil.getXiSqLite(i), ConfigUtil.mi_ma, null);
                Cursor cursor=xiSqLite.query("tableNames", null, null, null, null, null, null, null);
                int n=cursor.getCount();
                int number=0;
                int read=0;
                if (cursor.moveToFirst()){
                    do {
                        number = number + cursor.getInt(cursor.getColumnIndex("number"));
                        read = read + cursor.getInt(cursor.getColumnIndex("read"));
                    }while (cursor.moveToNext());
                }
                examSmallItems.get(i).setCurrent(read);
                examSmallItems.get(i).setMax(number);
                cursor.close();
            }
        }
        adapter.setData(examSmallItems);
        adapter.notifyDataSetChanged();
    }
}
