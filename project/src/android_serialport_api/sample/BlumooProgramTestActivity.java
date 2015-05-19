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


public class BlumooProgramTestActivity extends SerialPortActivity implements IopCallbacks {


	public enum ATE_STATE {
		ATE_STATE_IDLE,
		ATE_STATE_PROGRAM,
		ATE_STATE_VERIFY,
		ATE_STATE_MAC,
		ATE_STATE_ESN,
		ATE_STATE_ESN_REBOOT,
		ATE_STATE_VERIFY_MAC,
		ATE_STATE_VERIFY_ESN,
		ATE_STATE_AUDIO,
		ATE_STATE_LED_IR,
		
		ATE_STATE_BLUETOOTH,

		ATE_STATE_LED_RGB,
		ATE_STATE_FT_IR,		/* Final test for IR exteneded	*/
	}

	//Constants
	private static final int DEFAULT_TIMEOUT = 2000;	/* millseconds	*/
	private static final int BT_TEST_TIMEOUT = 10000;	/* millseconds	*/
	
	//variables
	private InputStream input;
	private int program_status_counter;
	private EditText edittext;
	private boolean ate_running;
	private boolean dut_communication;
	private int my_esn;
	private byte[]	my_bt_mac = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
	private ATE_STATE my_ate_state;
	private Timer ate_timer;
	private Iop myIop;
	private int file_checksum;
	EditText mReception;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		 //Remove title bar
	    this.requestWindowFeature(Window.FEATURE_NO_TITLE);

	    //Remove notification bar
	    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.blumoo_program_test);
		input = getResources().openRawResource(R.raw.combined_sw_bl_106_app_1_2_00);
		program_status_counter = 0;
		my_esn = 0;
		ate_running = false;
		dut_communication = false;
		my_ate_state = ATE_STATE.ATE_STATE_IDLE;
		
		file_checksum = 0;
		
		myIop = new Iop(this);
				
		mReception = (EditText) findViewById(R.id.editTextBlumooAteStatus);
			
	    final Button buttonBlumooAteStart = (Button)findViewById(R.id.buttonBlumooAteStart);
	    buttonBlumooAteStart.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				ate_start();
			}
		});	

	    this.reset_ate();
	    
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

							// Calculate 32-bit checksum of input file
							for( int i = 0; i < 1024; i+= 4)
							{
								file_checksum += ( ( file_buffer[i+3] & 0xff ) << 24  | ( file_buffer[i+2] & 0xff ) << 16 | ( file_buffer[i+1] & 0xff ) << 8 | ( file_buffer[i] & 0xff ) );
							}
//					        System.out.println("File checksum for currently is is: " + file_checksum);
										
							mOutputStream.write(file_buffer);
							program_status_counter ++;
							mReception.setText(Integer.toString(program_status_counter*100/256) + "%  固件  Programming" );
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
						break;
						
					case '0':
						switch(buffer[1]) {
						case 'i':
							mReception.append(" --- FAILED");
							ate_state_machine();
							break;
						default: 
							ate_state_machine_reset(false);
							break;
						}						
						break;
						
					case '1':
						switch(buffer[1]) {
							case 'p':
								ate_state_machine();
								break;
								
							case 'd':
								break;
								
							case 'a':
								ate_state_machine();
								break;
								
							case 'c':
								ate_state_machine();
								break;
								
							case 'i':
								mReception.append(" PASSED");
								ate_state_machine();
								break;
								
							case 'r':
								ate_state_machine();
								break;
								
							case 'b':
								ate_state_machine();
								break;

							case 'v':
								mReception.append(" PASSED");
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
		edittext.selectAll();
		edittext.requestFocus();
		
		this.reset_ate();
		try {
		    Thread.sleep(200);
		} catch(InterruptedException ex) {
		    Thread.currentThread().interrupt();
		}
		
		ate_running = false;
		dut_communication = false;
		
		my_esn = 0;
		for( int i = 0; i < my_bt_mac.length; i++ )
		{
			my_bt_mac[i] = 0;
		}
		
		file_checksum = 0;
		
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
				input.reset(); 						//seek to beginning of file
				mReception.setTextColor(Color.WHITE);
				mReception.setBackgroundColor(Color.TRANSPARENT);
				mReception.setText("Started...");	//Reset status window
				//mReception.setTextSize(25);
				program_status_counter = 0;
				ate_running = true;
				dut_communication = false;
				
				mOutputStream.write((byte)'p'); 	//p

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
			break;
			
		case ATE_STATE_VERIFY:			
			mReception.setText("编程完成 Programming Compete");
			mReception.append("\n验证图片 Verifying Image");
			try {
			    Thread.sleep(2000);
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
			
			try {
				byte[] result = new byte[4];
				
				result[3] = (byte)((file_checksum & 0xFF000000) >> 24);
				result[2] = (byte)((file_checksum & 0x00FF0000) >> 16);
				result[1] = (byte)((file_checksum & 0x0000FF00) >> 8);
				result[0] = (byte)((file_checksum & 0x000000FF) >> 0);

				mOutputStream.write((byte)'v');		//v
				
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				mOutputStream.write( result );
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;			
			
		case ATE_STATE_MAC:	
			//mReception.setText("编程完成 Programming Compete");
			try {
			    Thread.sleep(2000);
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
			
			dut_communication = true;
			int bt_address_low = my_esn + 100000;
			byte[] bt_address = new byte[]  {(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x40, (byte)0x5c, (byte)0x34, (byte)0x00, (byte)0x00};																
			bt_address[0] = (byte) (bt_address_low >> 0);
			bt_address[1] = (byte) (bt_address_low >> 8);
			bt_address[2] = (byte) (bt_address_low >> 16);			
			
//			my_bt_mac = bt_address;
			System.arraycopy( bt_address, 0, my_bt_mac, 0, bt_address.length );
			
			mReception.append("\n设置蓝牙地址  Setting MAC");
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), DEFAULT_TIMEOUT);
			Iop.sendInst(Iop.IOP_INST_ID.IOP_BT_ADDR_DATA, bt_address, 8);
			break;
			
		case ATE_STATE_ESN:
			try {
			    Thread.sleep(1000);
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
			ate_timer.schedule(new TimeoutTask(), DEFAULT_TIMEOUT);
			Iop.sendInst(Iop.IOP_INST_ID.IOP_ESN_DATA, esn_bytes, 4);
			break;
			
		case ATE_STATE_ESN_REBOOT:
			try {
			    Thread.sleep(1000);
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
			mReception.append("\n重启  Reboot");
			try {
				dut_communication = false;
				mOutputStream.write((byte)'r'); 	//p					
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
			break;
			
		case ATE_STATE_VERIFY_MAC:
			try {
			    Thread.sleep(2000);
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
			
			dut_communication = true;
			byte[] mac_cmd_id = new byte[2];
			/*
			esn_bytes[0] = (byte) (Iop.IOP_CMND_MSG.IOP_BT_DOWNLOAD_BT_ADDR.);
			esn_bytes[1] = (byte) (Iop.IOP_CMND_MSG.IOP_BT_DOWNLOAD_BT_ADDR >> 8);
			*/
			mac_cmd_id[0] = (byte) 1;
			mac_cmd_id[1] = (byte) 0;
			mReception.append("\n验证蓝牙地址 Verifying MAC");
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), DEFAULT_TIMEOUT);
			Iop.sendInst(Iop.IOP_INST_ID.IOP_CMND_ID, mac_cmd_id, 2);
			break;
			
		case ATE_STATE_VERIFY_ESN:
			try {
			    Thread.sleep(200);
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
			
			dut_communication = true;
			byte[] esn_cmd_id = new byte[2];
			/*
			esn_bytes[0] = (byte) (Iop.IOP_CMND_MSG.IOP_DOWNLOAD_ESN.);
			esn_bytes[1] = (byte) (Iop.IOP_CMND_MSG.IOP_DOWNLOAD_ESN >> 8);
			*/
			esn_cmd_id[0] = (byte) 0;
			esn_cmd_id[1] = (byte) 0;
			mReception.append("\n验证 编号 Verifying ESN");
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), DEFAULT_TIMEOUT);
			Iop.sendInst(Iop.IOP_INST_ID.IOP_CMND_ID, esn_cmd_id, 2);
			break;
			
		case ATE_STATE_AUDIO:
			
			try {
			    Thread.sleep(200);
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
			
			dut_communication = false;
			mReception.append("\n音频测试  Audio Test");
			
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), DEFAULT_TIMEOUT);
			
			try {		
				mOutputStream.write((byte)'a');		//a
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			break;
			
		case ATE_STATE_LED_IR:
			dut_communication = false;
			mReception.append("\n红外线LED灯 IR LED Test");
			
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), DEFAULT_TIMEOUT);
			
			try {
				mOutputStream.write((byte)'i'); //i
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		
			break;

		case ATE_STATE_LED_RGB:
			ate_state_machine_reset(true);			
			break;
					
		case ATE_STATE_FT_IR:
			dut_communication = false;
			mReception.append("\nPerforming FT IR LED Test");
			try {
				mOutputStream.write((byte)'f'); //f
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
			break;
			
		case ATE_STATE_BLUETOOTH:
			dut_communication = false;
			mReception.append("\n蓝牙测试  Bluetooth Test");
			
			ate_timer = new Timer();
			ate_timer.schedule(new TimeoutTask(), BT_TEST_TIMEOUT);
			
			try {
				mOutputStream.write((byte)'b'); //b
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
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
			if( edittext.getText().length() >= 1)
			{
				my_esn = Integer.parseInt(edittext.getText().toString());			
				ate_state_machine();
			}
		}
	}

	@Override
	public void iopMsgHandler(int inst, byte[] data, int size) {
		byte[] product_data = {66, 108, 117, 109, 111, 111, 32, 65, 84, 69, 32, 118, 48, 46, 49};//"Blumoo ATE tester v0.1";
		int i;
		
		
		if(inst == Iop.IOP_INST_ID.PROD_RQST.ordinal()) 
		{
			mReception.append("\tProduct Request\n");
			Iop.sendInst(Iop.IOP_INST_ID.PROD_RQST_DATA, product_data, product_data.length);
		}
		else if(inst == Iop.IOP_INST_ID.PROD_RQST_DATA.ordinal()) 
		{
			mReception.append("\tProduct Request Data\n\t");
			mReception.append(new String(data));
			mReception.append("\n");
		}
		else if(inst == Iop.IOP_INST_ID.IOP_BT_ADDR_DATA.ordinal())
		{	boolean bt_mac_fail = false;	
			String mac_string = " --- ";
			
			for(int j=0; j<8; j++) {
				mac_string += String.format("%02x", (int)data[7-j]&0xFF);
			}
			
			for( i = 0; i < my_bt_mac.length; i++ )
			{
				if( data[i] != my_bt_mac[i] )
				{
					bt_mac_fail = true;
					break;
				}
			}
			
			mReception.append(mac_string);
			
			if( bt_mac_fail == true ) {
				
				ate_state_machine_reset(false);
			}
			else {
				ate_state_machine();
			}
			
			
		}
		else if(inst == Iop.IOP_INST_ID.IOP_ESN_DATA.ordinal() )
		{
			String esn_string = " --- ";	
			int	esn = ((data[3] & 0xff) << 24) | ((data[2] & 0xff) << 16) | ((data[1] & 0xff) << 8)  | (data[0] & 0xff);
			
			for(int j=0; j<4; j++) {
				esn_string += String.format("%02x", (int)data[3-j]&0xFF);
			}
			mReception.append(esn_string);
			
			if( esn != my_esn )
			{
				ate_state_machine_reset(false);				
			}
			else {
				ate_state_machine();
			}			
			
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
		case ATE_STATE_LED_RGB:
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
