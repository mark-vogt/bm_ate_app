package android_serialport_api;

import java.io.IOException;
import java.util.ArrayList;

import android.util.Log;
import android_serialport_api.sample.SerialPortActivity;


public class Iop extends SerialPortActivity{
	
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
		IOP_DOWNLOAD_ESN,			//0
		/*----------------------------------------------
		Bluetooth IOP commands
		----------------------------------------------*/
		IOP_BT_DOWNLOAD_BT_ADDR,		//1
		IOP_BT_CLEAR_PAIRED_DEV_LIST,	//2
		IOP_BT_BLE_STOP_ADVERTISING,	//3
		IOP_BT_BLE_DISCONNECT,			//4
		IOP_BT_BLE_START_ADVERTISING,	//5
		IOP_BT_GET_PAIRED_DEV_LIST,		//6
		LAST
	}

	public enum IOP_INST_ID {
		IR_CODE_DATA,		//0
		RGN_START,			//1
		RGN_DATA,       	//2
		RGN_STATUS,     	//3
		RGN_CMPLT,      	//4
		RGN_INFO,       	//5
		RGN_UPLOAD,     	//6
		PROD_RQST,      	//7
		PROD_RQST_DATA, 	//8
		IOP_ESN_DATA,		//9	 	
		IOP_CMND_ID,		//10
		IOP_SET_GPIO,		//11
		IOP_CLEAR_GPIO,		//12
		IOP_SET_PIN_DIR,	//13	
		IOP_SET_PIN_MUX,	//14	
		IOP_UNUSED_15,
		IOP_UNUSED_16,
		IOP_UNUSED_17,
		IOP_UNUSED_18,
		IOP_UNUSED_19,
		/*----------------------------------------------
		Bluetooth IOP instrument IDs
		----------------------------------------------*/
		IOP_BT_ADDR_DATA,			//20,
		IOP_BT_SET_CARRIER_FREQ,	//21,
		IOP_BT_CNCT_STATUS,			//22,
		IOP_BT_CNCT_STATUS_DATA,	//23,
		IOP_UNUSED_24,
		IOP_UNUSED_25,
		IOP_UNUSED_26,
		IOP_UNUSED_27,
		IOP_UNUSED_28,
		IOP_UNUSED_29,
		IOP_UNUSED_30,				

		/*----------------------------------------------
		Audio IOP instrument IDs
		----------------------------------------------*/
		IOP_PLAY_TONE,				//31
		
		LAST //Must be last
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
	private static IopCallbacks IopCb;
	private static IOP_RX_STATE rx_state;
	private static int iop_rx_msg_calc_chksm;
	private static int iop_rx_msg_num_esc_bytes;
	private static ArrayList<Byte> iop_rx_buffer = new ArrayList<Byte>();
	
	public Iop(IopCallbacks myCallback) {
		IopCb = myCallback;
		reset();
	}
	
	public static void reset() {
		iop_rx_buffer.clear();
		rx_state = IOP_RX_STATE.GET_HEADER_SYNC;
		iop_rx_msg_calc_chksm = 0;
		iop_rx_msg_num_esc_bytes = 0;
	}
	
	public static void sendInst( IOP_INST_ID inst, byte[] data, int size ) {
		byte[] msg;
		byte[] msg_del;
		int checksum = 0;			
		msg = new byte[iop_dma_hdr_size + iop_hdr_size + iop_tail_size + size];
		int msg_size = iop_dma_hdr_size + iop_hdr_size + iop_tail_size + size;
		int dma_size = iop_hdr_size+ iop_tail_size + size; 
				
		msg[0] = 0x04;
//		msg[1] = (byte) (( dma_size & 0xFF000000 ) >> 24);
//		msg[2] = (byte) ((dma_size & 0xFF0000) >> 16);
//		msg[3] = (byte) (( dma_size & 0xFF00 ) >> 8);
//		msg[4] = (byte) (dma_size & 0xFF);
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
			checksum += (int)(msg[i] & 0xFF);
		}
		
		msg[msg_size - 4] = (byte) ((checksum & 0xFF00) >> 8);
		msg[msg_size - 3] = (byte) (checksum & 0xFF);
		msg[msg_size - 2] = DLE_BYTE;
		msg[msg_size - 1] = ETX_BYTE;
		
		String log_string = "<-";	

		//find DLE_BYTES
		int del_cnt = 0;
		for(int i=6; i<msg.length-2; i++) {
			if(msg[i] == DLE_BYTE) {
				del_cnt ++;
			}
		}
		
		//add delimiter
		msg_del = new byte[msg.length + del_cnt];
		int j=0;
		for(int i=0; i<msg.length; i++) {
			msg_del[j] = msg[i];
			j++;
			
			if(i>5 && i<(msg.length-2) ) {
				if(msg[i] == DLE_BYTE) {
					msg_del[j] = DLE_BYTE;
					j++;
				}
			}
		}
		dma_size += del_cnt;
		msg_del[1] = (byte) (( dma_size & 0xFF000000 ) >> 24);
		msg_del[2] = (byte) ((dma_size & 0xFF0000) >> 16);
		msg_del[3] = (byte) (( dma_size & 0xFF00 ) >> 8);
		msg_del[4] = (byte) (dma_size & 0xFF);
		
		for(int i=0; i<msg_del.length; i++) {
			log_string += String.format("%02x", (int)msg_del[i]);			
		}
//		mReception.append(log_string);
//		mReception.append("\n");
		Log.v("ATE",log_string);
		
		try {
			mOutputStream.write((byte)'d');
			mOutputStream.write(msg_del);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static void parse_rx_data(final byte[] buffer, final int size) {
		
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
            	reset();
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
	                		IopCb.iopAckReceived();
	                	}
                    }
	               
	                /*------------------------------------------
	                Reset state machine and read in next message
	                ------------------------------------------*/
	                reset();
	                }

	            /*----------------------------------------------
	            Should never get here; restart state machine
	            ----------------------------------------------*/
	            else
	                {
	            	reset();
	                }
				break;
			default:
				//error
				reset();
				break;
			}
			

					
			
		}
	}
	
	private static void iopMsgReady() {
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

		Log.v("IOP",log_string);
		log_string = "";
		iop_rx_buffer.clear();	
		
		IopCb.iopMsgHandler(msg_inst, msg_data, msg_size);
	}
	
	
	private static boolean validate_rx_message() {
		int tailCheckSum = 0;
		
		/*----------------------------------------------------------
		Verify checksum
		----------------------------------------------------------*/
		//Adjust our calculated checksum to not account for bytes in message tail
		iop_rx_msg_calc_chksm    -= ( iop_rx_buffer.get(iop_rx_buffer.size()-4).byteValue() & 0xFF ) +
									( iop_rx_buffer.get(iop_rx_buffer.size()-3).byteValue() & 0xFF ) +
									( iop_rx_buffer.get(iop_rx_buffer.size()-2).byteValue() & 0xFF ) +
									( iop_rx_buffer.get(iop_rx_buffer.size()-1).byteValue() & 0xFF );
		int b1 = (iop_rx_buffer.get(iop_rx_buffer.size()-4).byteValue() & 0xff)<< 8;
		int b2 = (iop_rx_buffer.get(iop_rx_buffer.size()-3).byteValue()) & 0xff;
		tailCheckSum = b1 | b2;
		
		if( tailCheckSum != iop_rx_msg_calc_chksm )
		    {
		    return false;
		    }
		
		return true;
	}

	private static void copyByte(byte b) {
		iop_rx_msg_calc_chksm += ( b & 0xFF );
		iop_rx_buffer.add(b);
	}

	@Override
	protected void onDataReceived(byte[] buffer, int size) {
		IopCb.iopDataReceived(buffer, size);
	}
	
	

}
