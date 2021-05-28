package com.zqf.logservice;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static android.app.NotificationManager.IMPORTANCE_HIGH;

/**
 * 用于日志记录
 * Created by Administrator on 2017/9/7.
 */
public class LogService extends Service {
    private static final String TAG = "AppLog";
    private String CHANNEL_ONE_ID;
    private static final int SDCARD_LOG_FILE_MAX_SIZE = 5 * 1024 * 1024; // 内存中日志文件最大值，10M
    private static final int SDCARD_LOG_FILE_MONITOR_INTERVAL = 10 * 60 * 1000; // 内存中的日志文件大小监控时间间隔，10分钟
    private static final int SDCARD_LOG_FILE_SAVE_DAYS = 7; // sd卡中日志文件的最多保存天数
    private String LOG_PATH_SDCARD_DIR; // 日志文件在sdcard中的路径
    private String LOG_SERVICE_LOG_PATH; // 本服务产生的日志，记录日志服务开启失败信息
    private String logServiceLogName = "Log.log";// 本服务输出的日志文件名称
    private SimpleDateFormat myLogSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE);
    private OutputStreamWriter writer;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.CHINESE);// 日志名称格式
    private PowerManager.WakeLock wakeLock;
    private LogTaskReceiver logTaskReceiver;
    /*
     * 是否正在监测日志文件大小； 如果当前日志记录在SDcard中则为false 如果当前日志记录在内存中则为true
     */
    private boolean logSizeMoniting = false;
    private String MONITOR_LOG_SIZE_ACTION; // 日志文件监测action
    private String SWITCH_LOG_FILE_ACTION; // 切换日志文件action

    @Override
    public IBinder onBind(Intent intent) {
        return new MyBinder();
    }

    public class MyBinder extends Binder {

        public LogService getService() {
            return LogService.this;
        }
    }

    private static final int NOTIFICATION_ID = 1; // 如果id设置为0,会导致不能设置为前台service

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate is create");
        init();
        register();
        deploySwitchLogFileTask();
        logCollector();
    }

    private void startForeNotice() {
        String CHANNEL_ONE_NAME = "Channel One";
        NotificationChannel notificationChannel = null;
        Notification.Builder builder = new Notification.Builder(this);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                    CHANNEL_ONE_NAME, IMPORTANCE_HIGH);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setSound(null, null);
            notificationChannel.enableVibration(false);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(notificationChannel);
            builder.setChannelId(CHANNEL_ONE_ID);
        }
        builder.setTicker("log_service");
        builder.setContentTitle("log_Service");
        builder.setContentText("Make this log_service run in the foreground.");
        builder.setSound(null);
        startForeground(NOTIFICATION_ID, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeNotice();
        return Service.START_STICKY;
    }

    private void init() {
        Log.d(TAG, "log_init");
        MONITOR_LOG_SIZE_ACTION = getPackageName() + "MONITOR_LOG_SIZE";
        SWITCH_LOG_FILE_ACTION = getPackageName() + "SWITCH_LOG_FILE_ACTION";
        CHANNEL_ONE_ID = getPackageName() + "LOG";
        LOG_PATH_SDCARD_DIR = Environment.getExternalStorageDirectory()
                .getAbsolutePath()
                + File.separator
                + getPackageName()
                + File.separator
                + "log";
        createLogDir();
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LogService.class.getSimpleName());
        Log.i(TAG, "LogService onCreate");
    }

    private void register() {
        Log.d(TAG, "register");
        IntentFilter logTaskFilter = new IntentFilter();
        logTaskFilter.addAction(MONITOR_LOG_SIZE_ACTION);
        logTaskFilter.addAction(SWITCH_LOG_FILE_ACTION);
        logTaskReceiver = new LogTaskReceiver();
        registerReceiver(logTaskReceiver, logTaskFilter);
    }

    /**
     * 部署日志切换任务，每天过一个小时切换日志文件
     */
    private void deploySwitchLogFileTask() {
        Log.d(TAG, "deploySwitchLogFileTask");
        Intent intent = new Intent(SWITCH_LOG_FILE_ACTION);
        PendingIntent sender = PendingIntent.getBroadcast(this, 0, intent, 0);
        // 部署任务
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60 * 60 * 1000, //延时一个小时执行
                AlarmManager.INTERVAL_HOUR, sender);
    }

    /**
     * 日志收集 1.清除日志缓存 2.杀死应用程序已开启的Logcat进程防止多个进程写入一个日志文件 3.开启日志收集进程 4.处理日志文件 移动
     * OR 删除
     */
    class LogCollectorThread extends Thread {

        public LogCollectorThread() {
            super("LogCollectorThread");
            Log.d(TAG, "LogCollectorThread is create");
        }

        @Override
        public void run() {
            try {
                Log.d(TAG, "1111111111");
                wakeLock.acquire(); // 唤醒手机
                Log.d(TAG, "222222222222");
//                createLogCollector();
                Log.d(TAG, "33333333");
                Thread.sleep(1000);// 休眠，创建文件，然后处理文件，不然该文件还没创建，会影响文件删除
                handleLog();
                wakeLock.release(); // 释放
            } catch (Exception e) {
                e.printStackTrace();
                recordLogServiceLog(Log.getStackTraceString(e));
            }
        }
    }

    /**
     * 开始收集日志信息
     */
    public void createLogCollector() {
        logServiceLogName = sdf.format(new Date()) + ".txt";// 日志文件名称
        Log.d(TAG, "logServiceLogName:" + logServiceLogName);
        LOG_SERVICE_LOG_PATH = LOG_PATH_SDCARD_DIR + File.separator + logServiceLogName;
        Log.d(TAG, "LOG_SERVICE_LOG_PATH:" + LOG_SERVICE_LOG_PATH);
        try {
            if (writer != null) {
                Log.d(TAG, "write1111111111111");
                try {
                    writer.close();
                    writer = null;
                    Log.d(TAG, "write==null:" + (writer == null));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            writer = new OutputStreamWriter(new FileOutputStream(LOG_SERVICE_LOG_PATH, true), Charset.forName("gbk"));
            Log.d(TAG, "writer:" + writer.toString());
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        Log.d(TAG, "createLogCollector");
        recordLogServiceLog("start collecting the log,and log name is:" + logServiceLogName);
    }

    /**
     * 处理日志文件 1.如果日志文件存储位置切换到内存中，删除除了正在写的日志文件 并且部署日志大小监控任务，控制日志大小不超过规定值
     * 2.如果日志文件存储位置切换到SDCard中，删除7天之前的日志，移 动所有存储在内存中的日志到SDCard中，并将之前部署的日志大小 监控取消
     */
    public void handleLog() {
        deployLogSizeMonitorTask();
        deleteSDcardExpiredLog();
    }

    /**
     * 部署日志大小监控任务
     */
    private void deployLogSizeMonitorTask() {
        if (logSizeMoniting) { // 如果当前正在监控着，则不需要继续部署
            return;
        }
        logSizeMoniting = true;
        Intent intent = new Intent(MONITOR_LOG_SIZE_ACTION);
        PendingIntent sender = PendingIntent.getBroadcast(this, 0, intent, 0);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
                SDCARD_LOG_FILE_MONITOR_INTERVAL, sender);
        Log.d(TAG, "deployLogSizeMonitorTask() succ !");
    }

    /**
     * 取消部署日志大小监控任务
     */
    private void cancelLogSizeMonitorTask() {
        logSizeMoniting = false;
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(MONITOR_LOG_SIZE_ACTION);
        PendingIntent sender = PendingIntent.getBroadcast(this, 0, intent, 0);
        am.cancel(sender);
        Log.d(TAG, "canelLogSizeMonitorTask() succ");
    }

    /**
     * 检查日志文件大小是否超过了规定大小 如果超过了重新开启一个日志收集进程
     */
    private void checkLogSize() {
        File file = new File(LOG_SERVICE_LOG_PATH);
        if (file == null || !file.exists()) {
            return;
        }
        Log.d(TAG, "checkLog() ==> The size of the log is too big?");
        if (file.length() >= SDCARD_LOG_FILE_MAX_SIZE) {
            Log.d(TAG, "The log's size is too big!");
            logCollector();
        }
    }

    private void logCollector() {
        createLogCollector();
        new LogCollectorThread().start();
    }

    /**
     * 创建日志目录
     */
    private void createLogDir() {
        File file = new File(LOG_PATH_SDCARD_DIR);
        if (!file.isDirectory()) {
            file.mkdirs();
        }
    }

    /**
     * 删除内存下过期的日志
     */
    private void deleteSDcardExpiredLog() {
        File file = new File(LOG_PATH_SDCARD_DIR);
        if (file.isDirectory()) {
            File[] allFiles = file.listFiles();
            if (allFiles == null) {
                return;
            }
            for (File logFile : allFiles) {
                String fileName = logFile.getName();
                if (logServiceLogName.equals(fileName)) {
                    continue;
                }
                String createDateInfo = getFileNameWithoutExtension(fileName);
                if (canDeleteSDLog(createDateInfo)) {
                    logFile.delete();
                    Log.d(TAG, "delete expired log success,the log path is:"
                            + logFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * 判断sdcard上的日志文件是否可以删除
     *
     * @param createDateStr
     * @return
     */
    public boolean canDeleteSDLog(String createDateStr) {
        boolean canDel = false;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -1 * SDCARD_LOG_FILE_SAVE_DAYS);// 删除1天之前日志
        Date expiredDate = calendar.getTime();
        try {
            Date createDate = sdf.parse(createDateStr);
            canDel = createDate.before(expiredDate);
        } catch (ParseException e) {
            Log.e(TAG, e.getMessage(), e);
            canDel = false;
        }
        return canDel;
    }

    private Date time = new Date();

    /**
     * 记录日志服务的基本信息 防止日志服务有错，在LogCat日志中无法查找 此日志名称为Log.log
     *
     * @param msg
     */
    public void recordLogServiceLog(String msg) {
        time.setTime(System.currentTimeMillis());
        if (writer != null) {
            try {
                writer.write(myLogSdf.format(time) + " : \n" + msg);
                writer.write("\n");
                writer.flush();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage(), e);
            }
            /**
             * （主动刷新缓存）
             *  Google的一些开发者给出的方案是：在写入文件后，添加代码：
             */
            File file = new File(LOG_SERVICE_LOG_PATH);
            MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
            notifySystemToScan(file.getAbsolutePath());
        }
    }

    /**
     * 对于文件夹都找不到的问题
     *
     * @param filePath
     */
    private void notifySystemToScan(String filePath) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File file = new File(filePath);
        Uri uri = Uri.fromFile(file);
        intent.setData(uri);
        getApplication().sendBroadcast(intent);
    }

    /**
     * 去除文件的扩展类型（.log）
     *
     * @param fileName
     * @return
     */
    private String getFileNameWithoutExtension(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return System.currentTimeMillis() + "";
        }
        return fileName.substring(0, fileName.indexOf("."));
    }

    /**
     * 日志任务接收 切换日志，监控日志大小
     *
     * @author Administrator
     */
    class LogTaskReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (SWITCH_LOG_FILE_ACTION.equals(action)) {
                Log.d("TAG", "switch_log_file_action");
                recordLogServiceLog("switch_log_file_action");
                logCollector();
            } else if (MONITOR_LOG_SIZE_ACTION.equals(action)) {
                Log.d("TAG", "Size_action");
                recordLogServiceLog("size_action");
                checkLogSize();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recordLogServiceLog("LogService onDestroy");
        if (writer != null) {
            try {
                writer.close();
                writer = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        unregisterReceiver(logTaskReceiver);
        cancelLogSizeMonitorTask();
    }
}
