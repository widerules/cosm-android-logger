package doukas.cosm.androidresources;

import java.io.IOException;
import java.io.RandomAccessFile;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

//cosm
import Pachube.Data;
import Pachube.Feed;
import Pachube.Pachube;
import Pachube.PachubeException;

public class Background extends Service {

	private Handler mHandler = new Handler();
	private Handler cpuHandler = new Handler();
	private long mStartRX = 0;
	private long mStartTX = 0;
	public long SLEEP_TIME = 60000;
	public long CPU_THREAD = 10000;
	String CosmKEY = "QqDctZeA3mgd_BfiBav3dYaouyeSAKw3ZG5XNzIzeHl5Yz0g";
	RandomAccessFile reader;

	boolean cpu, memory, data, battery;
	
	float cpu_avg = 0f;
	long memory_avg = 0;
	
	int cpu_counter = 0;
	
	String id = "";
	String key = "";

	@Override
	public void onCreate() {
		
	}


	long memoryInfo() {
		MemoryInfo mi = new MemoryInfo();
		ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		activityManager.getMemoryInfo(mi);
		long availableMegs = mi.availMem / 1048576L;
		return availableMegs;
	}
	private float readCPU() {
		try {
			reader = new RandomAccessFile("/proc/stat", "r");
			String load = reader.readLine();

			String[] toks = load.split(" ");

			long idle1 = Long.parseLong(toks[5]);
			long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
					+ Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

			try {
				Thread.sleep(360);
			} catch (Exception e) {}

			reader.seek(0);
			load = reader.readLine();
			reader.close();

			toks = load.split(" ");

			long idle2 = Long.parseLong(toks[5]);
			long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
					+ Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

			return (float)(cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1))*100;

		} catch (IOException ex) {
			ex.printStackTrace();
		}

		return 0;
	} 
	
	
	private final Runnable cpuRunnable = new Runnable() {
		public void run() {
			float cpu = readCPU();

			long memory = memoryInfo();
			
			cpu_counter++;
			
			cpu_avg = (cpu + cpu_avg); 
			memory_avg = (memory + memory_avg); 
			
			
			
			cpuHandler.postDelayed(cpuRunnable, CPU_THREAD);
			
		}
	};


	private final Runnable mRunnable = new Runnable() {
		public void run() {
			
			long rxBytes = TrafficStats.getTotalRxBytes()- mStartRX;
			long txBytes = TrafficStats.getTotalTxBytes()- mStartTX;
			//reset
			mStartRX = TrafficStats.getTotalRxBytes();
			mStartTX = TrafficStats.getTotalTxBytes();
			double battlevel = getBatteryLevel();

			

			//update cosm:
			Pachube p = new Pachube(CosmKEY);
			try {
				Feed f = p.getFeed(64905);
				f.updateDatastream(0, (double)cpu_avg/cpu_counter);
				f.updateDatastream(1, (double)memory_avg/cpu_counter);
				System.out.println("cpu: "+cpu_avg/cpu_counter+" memory: "+memory_avg/cpu_counter);
				f.updateDatastream(2, (double)rxBytes/1000);
				f.updateDatastream(3, (double)txBytes/1000);
				f.updateDatastream(4, battlevel*100);
			} catch (PachubeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			cpu_counter = 0;
			cpu_avg = 0;
			memory_avg = 0;
			mHandler.postDelayed(mRunnable, SLEEP_TIME);
		}
	};

	void creatCosmDatastreams() throws PachubeException {
		Pachube p = new Pachube(key);
		Feed f = p.getFeed(Integer.parseInt(id));
		Data a = new Data();
		a.setId(0);
		a.setTag("CPU %");
		f.createDatastream(a);
		a.setId(1);
		a.setTag("Free Memory (MB)");
		f.createDatastream(a);
		a.setId(2);
		a.setTag("Data Rx (Kb)");
		f.createDatastream(a);
		a.setId(3);
		a.setTag("Data Tx (Kb)");
		f.createDatastream(a);
		a.setId(4);
		a.setTag("Battery Level (%)");
		f.createDatastream(a);
	}


	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onDestroy() {
		//code to execute when the service is shutting down
		super.onDestroy();
	}

	@Override
	public void onStart(Intent intent, int startid) {
		//code to execute when the service is starting up
		//read prefs:
		Bundle extras = intent.getExtras();
		if(extras !=null) {
			id = extras.getString("FeedID");
			key = extras.getString("CosmKey");
			String cpu = extras.getString("CPU");
			String ram = extras.getString("RAM");
			String data = extras.getString("DATA");
			String bat = extras.getString("BAT");
			String time = extras.getString("TIME");
			System.out.println(id);
			System.out.println(key);
			System.out.println(cpu);
			System.out.println(ram);
			System.out.println(data);
			System.out.println(bat);
			System.out.println(time);
			
			if(time.equals("1 minute")) {
				SLEEP_TIME = 60000;
			}
			if(time.equals("5 minutes")) {
				SLEEP_TIME = 300000;
			}
			if(time.equals("15 minutes")) {
				SLEEP_TIME = 900000;
			}
			if(time.equals("30 minutes")) {
				SLEEP_TIME = 1800000;
			}
			if(time.equals("1 hour")) {
				SLEEP_TIME = 3600000;
			}
			if(time.equals("2 hours")) {
				SLEEP_TIME = 7200000;
			}
			
			
			try {
				creatCosmDatastreams();
			} catch (PachubeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			mStartRX = TrafficStats.getTotalRxBytes();
			mStartTX = TrafficStats.getTotalTxBytes();
			mHandler.postDelayed(mRunnable, SLEEP_TIME);
			cpuHandler.postDelayed(cpuRunnable, CPU_THREAD);
		}
	}

	public double getBatteryLevel() {
		Intent batteryIntent = getApplicationContext().registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		int rawlevel = batteryIntent.getIntExtra("level", -1);
		double scale = batteryIntent.getIntExtra("scale", -1);
		double level = -1;
		if (rawlevel >= 0 && scale > 0) {
			level = rawlevel / scale;
		}

		return level;
	}


}
