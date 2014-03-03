package android_serialport_api.sample;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android_serialport_api.Iop;
import android_serialport_api.IopCallbacks;
import android_serialport_api.sample.BlumooProgramTestActivity.TimeoutTask;


public class BlumooFinalTestActivity extends SerialPortActivity implements IopCallbacks {


	public enum ATE_STATE {
		ATE_STATE_IDLE,
		ATE_STATE_BLUETOOTH,

		ATE_STATE_AUDIO,
		ATE_STATE_FT_IR,
		
		ATE_STATE_COMPLETE,
	}

	//Constants
	private static final int DEFAULT_TIMEOUT = 10000;	/* millseconds	*/
	private static final int BT_TEST_TIMEOUT = 10000;	/* millseconds	*/
	
	//variables
	//private EditText edittext;
	private boolean ate_running;
	private boolean dut_communication;
	private int my_esn;

	private ATE_STATE my_ate_state;
	private Timer ate_timer;
	private Iop myIop;
	
	EditText mReception;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		 //Remove title bar
	    this.requestWindowFeature(Window.FEATURE_NO_TITLE);

	    //Remove notification bar
	    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.blumoo_final_test);
		
		my_esn = 0;
		ate_running = false;
		dut_communication = false;
		my_ate_state = ATE_STATE.ATE_STATE_IDLE;
		myIop = new Iop(this);
				
		mReception = (EditText) findViewById(R.id.editTextBlumooAteStatus);
			
	    final Button buttonBlumooAteStart = (Button)findViewById(R.id.buttonBlumooAteStart);
	    buttonBlumooAteStart.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				ate_start();
			}
		});	

//	    edittext = (EditText) findViewById(R.id.editTextBlumooAteSerial);
//	    edittext.setSelectAllOnFocus(true);
//
//        edittext.setOnKeyListener(new EditText.OnKeyListener() {
//        public boolean onKey(View v, int keyCode, KeyEvent event) {
//        		Log.v("Mark",edittext.getText().toString());
//        		if (keyCode == KeyEvent.KEYCODE_ENTER) {
//        			if( edittext.getText().length() >= 1 ) {        				
//        				ate_start();
//        			}
//        		}
//                return false;
//            }
//        });
	}

	@Override
	protected void onDataReceived(final byte[] buffer, final int size) {
		runOnUiThread(new Runnable() {
			public void run() {
				if( dut_communication == true ) {
					String log_string = "<<<<<<<<<<<<<<<<<<<<<<<<";	
					
					for(int i=0; i<size; i++) {
						log_string += String.format("%02x", (int)buffer[i]);
					}
					Log.v("ATE",log_string);
					Iop.parse_rx_data(buffer, size);
				}
				else {
					String log_string = "------------------------";	
					
					for(int i=0; i<size; i++) {
						log_string += String.format("%02x", (int)buffer[i]);
					}
					Log.v("ATE",log_string);
					
					switch(buffer[0]) {
					case 0x10: //DLE_BYTE:
						Iop.parse_rx_data(buffer, size);
						break;
						
					case '0':
						ate_state_machine_reset(false);
						break;
						
					case '1':
						switch(buffer[1]) {							
							case 'h':
								ate_state_machine();
								break;
															
							case 'f':
								ate_state_machine();
								break;
															
							case 'b':
								ate_state_machine();
								break;
								
							default:
								break;
						};					
						break;
					default:
						break;
					};
				}
			}
		});
	}

	private void ate_state_machine_reset(boolean pass_fail) {
		if(pass_fail == true) {
			mReception.append("\n成功   SUCCESS!");
			mReception.setTextColor(Color.BLACK);
			mReception.setBackgroundColor(Color.GREEN);			
		}
		else {
			mReception.append("\n失败  FAILED!");
			mReception.setTextColor(Color.BLACK);
			mReception.setBackgroundColor(Color.RED);					
		}
		my_ate_state = ATE_STATE.ATE_STATE_IDLE;
		//edittext.selectAll();
		//edittext.requestFocus();
		
		this.reset_ate();
		try {
		    Thread.sleep(200);
		} catch(InterruptedException ex) {
		    Thread.currentThread().interrupt();
		}
		
		my_esn = 0;
		ate_running = false;
		dut_communication = false;
			
		Iop.reset();
	}
	
	private void ate_state_machine() {
		my_ate_state = ATE_STATE.values()[my_ate_state.ordinal() + 1];
		Log.v("ATE", "NEW STATE" + my_ate_state.name());
		
		if(ate_timer != null) {				
			ate_timer.cancel();
		}
		
		switch(my_ate_state) {
		case ATE_STATE_IDLE:
			break;

		case ATE_STATE_AUDIO:		
			mReception.append("\n音频测试 FT Audio Test");
			
			try {
			    Thread.sleep(1000);
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
			
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), DEFAULT_TIMEOUT);
			
			try {		
				mOutputStream.write((byte)'h');		//h
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			break;
					
		case ATE_STATE_FT_IR:
			dut_communication = false;
			mReception.append("\n红外线LED灯 FT IR LED Test");
					
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), DEFAULT_TIMEOUT);
			
			try {
				mOutputStream.write((byte)'f'); //f
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
			break;
			
		case ATE_STATE_BLUETOOTH:		
			mReception.setTextColor(Color.WHITE);
			mReception.setBackgroundColor(Color.TRANSPARENT);
			mReception.setText("蓝牙连接 Creating BLE connection...");	//Reset status window
			mReception.setTextSize(25);
			ate_running = true;			
			dut_communication = false;
			
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), BT_TEST_TIMEOUT);
			
			try {
				mOutputStream.write((byte)'b'); //b
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
			break;
			
		case ATE_STATE_COMPLETE:
			ate_state_machine_reset(true);			
			break;
			
		default:
			my_ate_state = ATE_STATE.ATE_STATE_IDLE;
			break;	
		}
	}
	
	private Handler TimeoutHandler = new Handler() {
	    public void handleMessage(Message msg) {
	    	ate_state_machine_reset(false);
	    }
	};
	
	 class TimeoutTask extends TimerTask {
	        public void run() {
	            Log.v("ATE","timeout");
	            ate_timer.cancel(); //Terminate the timer thread
	            TimeoutHandler.sendEmptyMessage(0);
	        }
	    }
	
	private void ate_start() {
		if(ate_running != true)
		{
//			if( edittext.getText().length() >= 1)
//			{
//			my_esn = Integer.parseInt(edittext.getText().toString());	
			ate_state_machine();
//			}
		}
	}

	@Override
	public void iopMsgHandler(int inst, byte[] data, int size) {
		
	}

	@Override
	public void iopAckReceived() {
		Log.v("ATE", "ack received");
		switch(my_ate_state) {
		case ATE_STATE_IDLE:
		case ATE_STATE_AUDIO:
		case ATE_STATE_BLUETOOTH:
		case ATE_STATE_COMPLETE:
		default:
			//ignore
			break;		
		}		
		
	}

	@Override
	public void iopDataReceived(byte[] buffer, int size) {
		onDataReceived(buffer, size);
		
	}
	

}
