package android_serialport_api.sample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;


public class IopActivity extends SerialPortActivity {
	//constants
	public static final int DLE_BYTE = 0x10;
	public static final int ETX_BYTE = 0x03;
	
	public static final int iop_dma_hdr_size = 5; 	//DMA header
	public static final int iop_hdr_size = 2+2+4; 	//DLE, Description, ID, Size
	public static final int iop_tail_size = 4; 		//chksum, DLE, ETX
	
	public static final int iop_hdr_offset_header_dle = 0;
	public static final int iop_hdr_size_header_dle = 1;
	public static final int iop_hdr_offset_descriptor = 1;
	public static final int iop_hdr_size_descriptor = 1;
	public static final int iop_hdr_offset_id = 2;
	public static final int iop_hdr_size_id = 2;
	public static final int iop_hdr_offset_data_length = 4;
	public static final int iop_hdr_size_data_length = 4;
	public static final int iop_tail_offset_chsm = 0;
	public static final int iop_tail_size_chsm = 2;
	public static final int iop_tail_offset_tail_dle = 2;
	public static final int iop_tail_size_tail_dle = 1;
	public static final int iop_tail_offset_tail_etx = 3;
	public static final int iop_tail_size_tail_etx = 1;
	
	//enums
	public enum IOP_MSG_DESCRIPTOR {
		NO_ACK_REQ, 	/* No ACK requested 				*/
		ACK_REQ,      	/* ACK requested                    */
	    ACK_RESP,      	/* ACK response                     */
	    INVLD,      	/* Invalid descriptor				*/
	    NUMR			/* Number of Message Descriptors    */
	}

	public enum IOP_CMND_MSG {
		START,
		LAST
	}
	
	public enum IOP_INST_ID {
		IR_CODE_DATA,
		RGN_START,
		RGN_DATA,
		RGN_STATUS,
		RGN_CMPLT,
		RGN_INFO,
		RGN_UPLOAD,
		PROD_RQST,
		PROD_RQST_DATA,
		
		LAST //Must be last
, 
	}
	
	public enum IOP_RX_STATE {
	    GET_HEADER_SYNC,            /* Header Start Byte                */
	    GET_HEADER_ACK,             /* Header ACK Byte                  */
	    GET_DATA,                   /* Generic Data Byte                */
	    GET_DATA_DLE,               /* Check for escaped DLE byte       */
	    STATE_NMBR                  /* Number of States                 */		
	}
	
	public enum IOP_EVNT_ID {
		START,
		LAST
	}
	
	//variables
	private IOP_RX_STATE rx_state;
	private int iop_rx_msg_calc_chksm;
	private int iop_rx_msg_num_esc_bytes;
	ArrayList<Byte> iop_rx_buffer = new ArrayList<Byte>();
	
	EditText mReception;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.blumootest);

		resetStateMachine();
		mReception = (EditText) findViewById(R.id.editTextBlumooAteSerial);
		
	    final Button buttonBlumooTest = (Button)findViewById(R.id.buttonBlumooAteStart);
	    buttonBlumooTest.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				iopSendInst(IOP_INST_ID.PROD_RQST,null, 0);
			}
		});	
	    
	    buttonBlumooTest.setOnLongClickListener(new View.OnLongClickListener() {
			
			@Override
			public boolean onLongClick(View v) {
				mReception.setText("");
				return false;
			}
		});
	    	
	    

	}

	@Override
	protected void onDataReceived(final byte[] buffer, final int size) {
		runOnUiThread(new Runnable() {
			public void run() {
				parse_rx_data(buffer, size);
			}
		});
		
	
	

	
	}
	
	public void iopSendInst( IOP_INST_ID inst, byte[] data, int size ) {
		byte[] msg;
		int checksum = 0;
		msg = new byte[iop_dma_hdr_size + iop_hdr_size + iop_tail_size + size];
		int msg_size = iop_dma_hdr_size + iop_hdr_size + iop_tail_size + size;
		int dma_size = iop_hdr_size+ iop_tail_size + size; 
		
		msg[0] = 0x04;
		msg[1] = (byte) (( dma_size & 0xFF000000 ) >> 24);
		msg[2] = (byte) ((dma_size & 0xFF0000) >> 16);
		msg[3] = (byte) (( dma_size & 0xFF00 ) >> 8);
		msg[4] = (byte) (dma_size & 0xFF);
		msg[5] = DLE_BYTE;
		msg[6] = 1;
		msg[7] = (byte) (( inst.ordinal() & 0xFF00 ) >> 8);
		msg[8] = (byte) (inst.ordinal() & 0xFF);
		msg[9] = (byte) (( size & 0xFF000000 ) >> 24);
		msg[10] = (byte) ((size & 0xFF0000) >> 16);
		msg[11] = (byte) (( size & 0xFF00 ) >> 8);
		msg[12] = (byte) (size & 0xFF);
		
		if(size > 0) {
			System.arraycopy(data, 0, msg, iop_dma_hdr_size + iop_hdr_size, size);
		}
		
		for(int i=iop_dma_hdr_size; i<msg.length; i++) { //calc checksum
			checksum += msg[i];
		}
		
		msg[msg_size - 4] = (byte) ((checksum & 0xFF00) >> 8);
		msg[msg_size - 3] = (byte) (checksum & 0xFF);
		msg[msg_size - 2] = DLE_BYTE;
		msg[msg_size - 1] = ETX_BYTE;
		
		String log_string = "<-";	

		
		for(int i=0; i<msg.length; i++) {
			log_string += String.format("%02x", (int)msg[i]);
		}
		mReception.append(log_string);
		mReception.append("\n");
		
		try {
			mOutputStream.write(msg);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private void copyByte(byte b) {
		iop_rx_msg_calc_chksm += b;
		iop_rx_buffer.add(b);
	}
	
	private void resetStateMachine() {
		iop_rx_buffer.clear();
		rx_state = IOP_RX_STATE.GET_HEADER_SYNC;
//		iop_rx_msg_total_length = 0;
		iop_rx_msg_calc_chksm = 0;
		iop_rx_msg_num_esc_bytes = 0;
	}
	
	private boolean validate_rx_message() {
		int tailCheckSum = 0;
		
		/*----------------------------------------------------------
		Verify checksum
		----------------------------------------------------------*/
		//Adjust our calculated checksum to not account for bytes in message tail
		iop_rx_msg_calc_chksm    -= iop_rx_buffer.get(iop_rx_buffer.size()-4).byteValue() +
									iop_rx_buffer.get(iop_rx_buffer.size()-3).byteValue() +
									iop_rx_buffer.get(iop_rx_buffer.size()-2).byteValue() +
									iop_rx_buffer.get(iop_rx_buffer.size()-1).byteValue();
		int b1 = (iop_rx_buffer.get(iop_rx_buffer.size()-4).byteValue() & 0xff)<< 8;
		int b2 = (iop_rx_buffer.get(iop_rx_buffer.size()-3).byteValue()) & 0xff;
		tailCheckSum = b1 | b2;
		
		if( tailCheckSum != iop_rx_msg_calc_chksm )
		    {
		    return false;
		    }
		
		return true;
	}
	
	private void parse_rx_data(final byte[] buffer, final int size) {
		
		for(int i = 0; i < size; i = i+1) {
			switch(rx_state) {
	        /*--------------------------------------------------
	        Look for beginning of a new message
	        --------------------------------------------------*/
			case GET_HEADER_SYNC:
				if(buffer[i] == DLE_BYTE) {
					rx_state = IOP_RX_STATE.GET_HEADER_ACK;
					copyByte( buffer[i]);
				}
				break;
			case GET_HEADER_ACK:
	            if( buffer[i] < IOP_MSG_DESCRIPTOR.INVLD.ordinal() ) {
	            	copyByte( buffer[i]);
	            	rx_state = IOP_RX_STATE.GET_DATA;
                }
            else
                {
            	resetStateMachine();
                }
				break;
			case GET_DATA:
				/*-------------------------------------------------------
	            Either beginning of message tail or escapted data byte
	            -------------------------------------------------------*/
				if( buffer[i] == DLE_BYTE ) {
					rx_state = IOP_RX_STATE.GET_DATA_DLE;
				}
				copyByte( buffer[i]);
				break;
			case GET_DATA_DLE:
				/*----------------------------------------------
	            Escaped data byte
	            ----------------------------------------------*/
	            if( buffer[i] == DLE_BYTE )
	                {
	                rx_state = IOP_RX_STATE.GET_DATA;
	                iop_rx_msg_num_esc_bytes++;
	                }

	            /*----------------------------------------------
	            End of message detected
	            ----------------------------------------------*/
	            else if( buffer[i] == ETX_BYTE )
	                {
	            	copyByte( buffer[i]);

	                /*------------------------------------------
	                Validate message then send for processing
	                ------------------------------------------*/
	                if( validate_rx_message() ) {
	                	if( iop_rx_buffer.get(1) == IOP_MSG_DESCRIPTOR.NO_ACK_REQ.ordinal() ) {
	                		iopMsgReady();
	                	}
	                	else if( iop_rx_buffer.get(1) == IOP_MSG_DESCRIPTOR.ACK_RESP.ordinal() ) {
	                		
	                	}
	                    
                    }

	                /*------------------------------------------
	                Reset state machine and read in next message
	                ------------------------------------------*/
	                resetStateMachine();
	                }

	            /*----------------------------------------------
	            Should never get here; restart state machine
	            ----------------------------------------------*/
	            else
	                {
	            	resetStateMachine();
	                }
				break;
			default:
				//error
				resetStateMachine();
				break;
			}
			

					
			
		}
	}
	
	private void iopMsgReady() {
		String log_string = "->";	
		int msg_size = 0;
		int msg_inst;
		 
		
		for(int i=0; i<iop_rx_buffer.size(); i++) {
			log_string += String.format("%02x", (int)iop_rx_buffer.get(i).byteValue());
		}
		
		msg_inst = (iop_rx_buffer.get(iop_hdr_offset_id).byteValue() << 8) | iop_rx_buffer.get(iop_hdr_offset_id+1).byteValue(); 
		msg_size = iop_rx_buffer.size() - iop_hdr_size - iop_tail_size;
		byte[] msg_data = new byte[msg_size];
		
		for(int i=0; i<msg_size; i++) {
			msg_data[i] = iop_rx_buffer.get(iop_hdr_size + i).byteValue();
		}
		

		mReception.append(log_string);
		mReception.append("\n");
		Log.v("IOP",log_string);
		log_string = "";
		iop_rx_buffer.clear();	
		
		iopMsgHandler(msg_inst, msg_data, msg_size);
	}
	
	private void iopMsgHandler(int inst, byte[] data, int size) {
		byte[] product_data = {66, 108, 117, 109, 111, 111, 32, 65, 84, 69, 32, 118, 48, 46, 49};//"Blumoo ATE tester v0.1";
		
		if(inst == IOP_INST_ID.PROD_RQST.ordinal()) {
			mReception.append("\tProduct Request\n");
			iopSendInst(IOP_INST_ID.PROD_RQST_DATA, product_data, product_data.length);
		}
		else if(inst == IOP_INST_ID.PROD_RQST_DATA.ordinal()) {
			mReception.append("\tProduct Request Data\n\t");
			mReception.append(new String(data));
			mReception.append("\n");
		}
		
	}
	
}
