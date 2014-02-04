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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android_serialport_api.Iop;
import android_serialport_api.IopCallbacks;




public class BlumooProgramTestActivity extends SerialPortActivity implements IopCallbacks {

	public enum ATE_STATE {
		ATE_STATE_IDLE,
		ATE_STATE_PROGRAM,
		ATE_STATE_MAC,
		ATE_STATE_ESN,
		ATE_STATE_ESN_REBOOT,
		ATE_STATE_LED_RED,
		ATE_STATE_LED_GREEN,
		ATE_STATE_LED_BLUE,
		ATE_STATE_LED_IR,
		ATE_STATE_AUDIO,
		ATE_STATE_BLUETOOTH
	}
	
	//variables
	private InputStream input;
	private int program_status_counter;
	private EditText edittext;
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
		setContentView(R.layout.blumoo_program_test);
		input = getResources().openRawResource(R.raw.combined_sw_106);
		program_status_counter = 0;
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

	    edittext = (EditText) findViewById(R.id.editTextBlumooAteSerial);
	    edittext.setSelectAllOnFocus(true);

        edittext.setOnKeyListener(new EditText.OnKeyListener() {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
        		Log.v("Mark",edittext.getText().toString());
        		if (keyCode == KeyEvent.KEYCODE_ENTER) {
        			if( edittext.getText().length() >= 1 ) {        				
        				ate_start();
        			}
        		}
                return false;
            }
        });
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
					case 'p':
						byte[] file_buffer = new byte[1024];									
						
						try {
							input.read(file_buffer, 0, 1024);								
							mOutputStream.write(file_buffer);
							program_status_counter ++;
							mReception.setText(Integer.toString(program_status_counter*100/256) + "%  固件  Programming" );
							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						break;
					case '0':
						ate_state_machine_reset(false);
						break;
					case '1':
						switch(buffer[1]) {
							case 'p':
								ate_state_machine();
								break;
							case 'd':
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
			mReception.append("\n成功  SUCCESS!");
			mReception.setTextColor(Color.BLACK);
			mReception.setBackgroundColor(Color.GREEN);			
		}
		else {
			mReception.append("\n失败  FAILED!");
			mReception.setTextColor(Color.BLACK);
			mReception.setBackgroundColor(Color.RED);					
		}
		my_ate_state = ATE_STATE.ATE_STATE_IDLE;
		edittext.selectAll();
		edittext.requestFocus();	
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
		case ATE_STATE_PROGRAM:
			try {
				mOutputStream.write((byte)'p'); 	//p
				input.reset(); 				//seek to beginning of file
				mReception.setText("Started...");		//Reset status window
				mReception.setTextColor(Color.WHITE);
				mReception.setBackgroundColor(Color.TRANSPARENT);
				mReception.setTextSize(25);
				program_status_counter = 0;
				ate_running = true;
				dut_communication = false;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
			break;
		case ATE_STATE_MAC:	
			//mReception.setText("Programming Compete!");
			try {
			    Thread.sleep(3000);
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
			
			dut_communication = true;
			int bt_address_low = my_esn + 100000;
			byte[] bt_address = new byte[]  {(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x40, (byte)0x5c, (byte)0x34, (byte)0x00, (byte)0x00};																
			bt_address[0] = (byte) (bt_address_low >> 0);
			bt_address[1] = (byte) (bt_address_low >> 8);
			bt_address[2] = (byte) (bt_address_low >> 16);			

			mReception.append("\n设置MAC地址  Setting MAC");
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), 2000);
			Iop.sendInst(Iop.IOP_INST_ID.IOP_BT_ADDR_DATA, bt_address, 8);
			break;
		case ATE_STATE_ESN:
			try {
			    Thread.sleep(500);
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
			
			dut_communication = true;
			byte[] esn_bytes = new byte[4];																
			esn_bytes[0] = (byte) (my_esn >> 0);
			esn_bytes[1] = (byte) (my_esn >> 8);
			esn_bytes[2] = (byte) (my_esn >> 16);
			esn_bytes[3] = (byte) (my_esn >> 24);
			mReception.append("\n编号  ESN = ");
			mReception.append(Integer.toString(my_esn));
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), 2000);
			Iop.sendInst(Iop.IOP_INST_ID.IOP_ESN_DATA, esn_bytes, 4);
			break;
		case ATE_STATE_ESN_REBOOT:
			mReception.append("\n重启  Reboot");
			try {
				dut_communication = false;
				mOutputStream.write((byte)'r'); 	//p					
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
			
			try {
			    Thread.sleep(2000);
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
			//break;
		case ATE_STATE_LED_RED:
			ate_state_machine_reset(true);			
			break;
		case ATE_STATE_LED_GREEN:
			break;
		case ATE_STATE_LED_BLUE:
			break;
		case ATE_STATE_LED_IR:
			break;
		case ATE_STATE_AUDIO:
			break;
		case ATE_STATE_BLUETOOTH:
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
			my_esn = Integer.parseInt(edittext.getText().toString());			
			ate_state_machine();			
		}
	}

	@Override
	public void iopMsgHandler(int inst, byte[] data, int size) {
		byte[] product_data = {66, 108, 117, 109, 111, 111, 32, 65, 84, 69, 32, 118, 48, 46, 49};//"Blumoo ATE tester v0.1";
		
		if(inst == Iop.IOP_INST_ID.PROD_RQST.ordinal()) {
			mReception.append("\tProduct Request\n");
			Iop.sendInst(Iop.IOP_INST_ID.PROD_RQST_DATA, product_data, product_data.length);
		}
		else if(inst == Iop.IOP_INST_ID.PROD_RQST_DATA.ordinal()) {
			mReception.append("\tProduct Request Data\n\t");
			mReception.append(new String(data));
			mReception.append("\n");
		}
		
	}

	@Override
	public void iopAckReceived() {
		Log.v("ATE", "ack received");
		switch(my_ate_state) {
		case ATE_STATE_IDLE:
		case ATE_STATE_PROGRAM:
		case ATE_STATE_LED_IR:
		case ATE_STATE_AUDIO:
		case ATE_STATE_BLUETOOTH:
		case ATE_STATE_ESN_REBOOT:
		case ATE_STATE_LED_RED:
		case ATE_STATE_LED_GREEN:
		case ATE_STATE_LED_BLUE:
		default:
			//ignore
			break;
		case ATE_STATE_MAC:	
		case ATE_STATE_ESN:
			ate_state_machine();
			break;
			
		}		
		
	}

	@Override
	public void iopDataReceived(byte[] buffer, int size) {
		onDataReceived(buffer, size);
		
	}
	
	


	
	
	

	
}
