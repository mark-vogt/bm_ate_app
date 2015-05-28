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


public class K10FWUpdateActivity extends SerialPortActivity implements IopCallbacks {


	public enum ATE_STATE {
		ATE_STATE_IDLE,

		ATE_STATE_QUERY_FW_VERSION,
		ATE_STATE_K10_PRGM_START,
		ATE_STATE_K10_PRGM,
		ATE_STATE_K10_PRGM_DONE,

		ATE_STATE_DONE
	}

	//Constants
	private static final int DEFAULT_TIMEOUT = 10 * 1000;	/* milliseconds	*/
	private static final int PROGRAM_TIMEOUT = 60 * 1000;	/* milliseconds	*/
	
	//variables
	private InputStream input;
	private int program_status_counter;
	private int	program_offset;
	private boolean ate_running;
	private boolean dut_communication;
	private ATE_STATE my_ate_state;
	private Timer ate_timer;
	private Iop myIop;
	
	TextView mReception;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.k10_fw_update);
		input = getResources().openRawResource(R.raw.ate_app_sw_1_1_00_crc);
		program_status_counter = 0;
		program_offset = 0;
		ate_running = false;
		dut_communication = false;
		my_ate_state = ATE_STATE.ATE_STATE_IDLE;

		myIop = new Iop(this);
		
	    final Button buttonK10FWUpdateStart = (Button)findViewById(R.id.buttonK10FWUpdateStart);
	    buttonK10FWUpdateStart.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				ate_start();
			}
		});
	    
	    this.reset_ate();
	    
		mReception = (TextView) findViewById(R.id.TextView1);	    
		mReception.setSelectAllOnFocus(true);

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
					
					case 'q':					
//						String ver_string = "<<<<<<<<<<<<<<<<<<<<<<<<";	
//						
//						for(int i=0; i<size; i++) {
//							ver_string += String.format("%02x", (int)buffer[i]);
//						}
//						Log.v("ATE",ver_string);
						
						byte[] desc_buffer = new byte[16];
						
						int	bld_ver = buffer[2] << 8 | buffer[1]; 
						int	minor_ver = buffer[3];
						int	major_ver = buffer[4];

						for( int i = 0; i < desc_buffer.length; i++ )
						{
							desc_buffer[i] = buffer[i+5];
						}
						
						mReception.append( "\nK10 FW version: " + Integer.toString(major_ver) + "." + Integer.toString(minor_ver) + "." + Integer.toString(bld_ver) );
						mReception.append( "\nDescription: " + new String( desc_buffer ) );						
						break;
							
					case 's':
						break;
						
					case 'w':
						byte[] offset = new byte[4];
						byte[] file_buffer = new byte[512];									
						
						try {
							offset[3] = (byte)((program_offset & 0xFF000000) >> 24);
							offset[2] = (byte)((program_offset & 0x00FF0000) >> 16);
							offset[1] = (byte)((program_offset & 0x0000FF00) >> 8);
							offset[0] = (byte)((program_offset & 0x000000FF) >> 0);
							
							input.read(file_buffer, 0, 512);

							mOutputStream.write( offset );			
							mOutputStream.write(file_buffer);
							program_status_counter++;
							mReception.setText(Integer.toString(program_status_counter*100/ ( 140 * 2 ) ) + "%  固件  Programming" );
//							try {
//								Thread.sleep(100);
//							} catch (InterruptedException e) {
//								// TODO Auto-generated catch block
//								e.printStackTrace();
//							}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						program_offset += 512;
						
						break;
						
					case 'z':
						break;
						
					case '0':
						switch(buffer[1]) {
//						case 'i':
//							mReception.append(" --- FAILED");
//							ate_state_machine();
//							break;
						default: 
							ate_state_machine_reset(false);
							break;
						}
						
						break;
						
					case '1':
						switch(buffer[1]) {

							case 'q':
								ate_state_machine();
								break;
								
							case 's':
								ate_state_machine();
								break;
								
							case 'w':
								//PRC - Will the K10 ever send this command??
								ate_state_machine();
								break;
								
							case 'z':
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
		if(ate_timer != null) {				
			ate_timer.cancel();
		}
		
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
		
		//mReception.selectAll();
		mReception.requestFocus();
		
		program_status_counter = 0;
		program_offset = 0;
			
		this.reset_ate();
		try {
		    Thread.sleep(200);
		} catch(InterruptedException ex) {
		    Thread.currentThread().interrupt();
		}
		
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

		case ATE_STATE_QUERY_FW_VERSION:
			mReception.append("\nChecking K10 FW Version");
			
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), DEFAULT_TIMEOUT);
			
			try {
				mOutputStream.write((byte)'q');		//q
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			break;
			
		case ATE_STATE_K10_PRGM_START:
			try {
			    Thread.sleep(3000);
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
			
			program_status_counter = 0;
			program_offset = 0;
			try {
				input.reset();
			} catch (IOException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			} 						//seek to beginning of file
			
			mReception.setTextColor(Color.WHITE);
			mReception.setBackgroundColor(Color.TRANSPARENT);
			mReception.setText("Started...");	//Reset status window
			
			byte[] region = new byte[4];

//			typedef uint32 RGN_data_t32; enum	/* data region type         */
//		    {
//			RGN_XLDR_CODE			= 0,	/* x-loader code					*/
//		    RGN_BB_CODE             = 1,    /* boot block code                  */
//		    RGN_RAM_CODE            = 2,   	/* code to run from ram             */
//		    RGN_NONVOL              = 3,   	/* non-volatile data                */
//		    RGN_SYS_CODE            = 4,   	/* system code                      */
//		    RGN_SPLASH_SCREEN       = 5,   	/* Logo image data                  */
//
//		    RGN_TEST				= 255,	/* Test Region						*/
//		    };
			
			region[3] = 0; // (byte)((file_checksum & 0xFF000000) >> 24);
			region[2] = 0; // (byte)((file_checksum & 0x00FF0000) >> 16);
			region[1] = 0; // (byte)((file_checksum & 0x0000FF00) >> 8);
			region[0] = 4; // (byte)((file_checksum & 0x000000FF) >> 0);
			
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), DEFAULT_TIMEOUT);
			
			try {		
				mOutputStream.write((byte)'s');		//s
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			try {
				mOutputStream.write( region );
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}		
			break;
			
		case ATE_STATE_K10_PRGM:
			
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), PROGRAM_TIMEOUT);
			
			try {		
				mOutputStream.write((byte)'w');		//w
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;

		case ATE_STATE_K10_PRGM_DONE:
			
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), DEFAULT_TIMEOUT);
			
			try {		
				mOutputStream.write((byte)'z');		//z
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;

		case ATE_STATE_DONE:
			mReception.append("\nK10 FW Update Complete");
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
			ate_state_machine();
		}
	}

	@Override
	public void iopMsgHandler(int inst, byte[] data, int size) {
		byte[] product_data = {66, 108, 117, 109, 111, 111, 32, 65, 84, 69, 32, 118, 48, 46, 49};//"Blumoo ATE tester v0.1";
		int i;
		
        Log.v("IOPMSGHNDLR", String.valueOf(inst) );
		
//		if(inst == Iop.IOP_INST_ID.PROD_RQST.ordinal()) 
//		{
//			mReception.append("\tProduct Request\n");
//			Iop.sendInst(Iop.IOP_INST_ID.PROD_RQST_DATA, product_data, product_data.length);
//		}
//		else if(inst == Iop.IOP_INST_ID.PROD_RQST_DATA.ordinal()) 
//		{
//			mReception.append("\tProduct Request Data\n\t");
//			mReception.append(new String(data));
//			mReception.append("\n");
//		}
//		else if(inst == Iop.IOP_INST_ID.IOP_BT_ADDR_DATA.ordinal())
//		{		
//			for( i = 0; i < my_bt_mac.length; i++ )
//			{
//				if( data[i] != my_bt_mac[i] )
//				{
//					ate_state_machine_reset(false);
//					return;
//				}
//			}
//			ate_state_machine();
//		}
//		else if(inst == Iop.IOP_INST_ID.IOP_ESN_DATA.ordinal() )
//		{
//			int	esn = ((data[3] & 0xff) << 24) | ((data[2] & 0xff) << 16) | ((data[1] & 0xff) << 8)  | (data[0] & 0xff);
//			
//			if( esn != my_esn )
//			{
//				ate_state_machine_reset(false);				
//			}
//			else
//			{
//				ate_state_machine();
//			}
//		}
//		else if(inst == Iop.IOP_INST_ID.IOP_IR_LEARNING_TEST_RSLT.ordinal() )
//		{
//			int result = (data[0] & 0xff);
//			
//			if( result == 0 )
//			{
//				ate_state_machine_reset(false);
//			}
//			else
//			{
//				ate_state_machine();
//			}
//		}
		
	}

	@Override
	public void iopAckReceived() {
		Log.v("ATE", "ack received");
		switch(my_ate_state) {
		case ATE_STATE_IDLE:
		case ATE_STATE_QUERY_FW_VERSION:
		case ATE_STATE_K10_PRGM_START:
		case ATE_STATE_K10_PRGM:
		case ATE_STATE_K10_PRGM_DONE:
		case ATE_STATE_DONE:
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
