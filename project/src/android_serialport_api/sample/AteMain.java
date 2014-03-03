package android_serialport_api.sample;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

public class AteMain extends Activity implements View.OnClickListener{

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	     
	    //set fullscreen
	    requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                                WindowManager.LayoutParams.FLAG_FULLSCREEN);
	    
	    
	    setContentView(R.layout.ate_main);
	    
	    ImageView launchProgramAte = (ImageView) findViewById(R.id.imageViewProgram);
	    ImageView launchFinalAte = (ImageView) findViewById(R.id.imageViewFinal);
	    ImageView launchSettings = (ImageView) findViewById(R.id.imageViewSettings);

	    launchProgramAte.setOnClickListener(this);
	    launchFinalAte.setOnClickListener(this);
	    launchSettings.setOnClickListener(this);
	    
	    // TODO Auto-generated method stub
	}


	@Override
	public void onClick(View arg0) {
		switch(arg0.getId()) {
		case R.id.imageViewSettings:
			startActivity(new Intent(this, BlumooProgramTestActivity.class));
			break;
		case R.id.imageViewFinal:
			startActivity(new Intent(this, BlumooFinalTestActivity.class));
			break;
		case R.id.imageViewProgram:
			startActivity(new Intent(this, MainMenu.class));
			break;
		default:
			break;
		
		}
		
	}

}
