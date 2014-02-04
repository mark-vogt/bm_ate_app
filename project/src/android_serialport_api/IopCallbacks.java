package android_serialport_api;

public interface IopCallbacks {
	public void iopMsgHandler(int inst, byte[] data, int size);
	public void iopAckReceived();
	public void iopDataReceived(byte[] buffer, int size);

}
