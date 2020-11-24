# LogServiceMaster
Step 1. Add it in your root build.gradle at the end of repositories:

       allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
  
  Step 2. Add the dependency:

  	dependencies {
	        implementation 'com.github.zhangqufa:LogServiceMaster:1.0.1'
	}
  
  Step 3. register Service in AndroidMainfest.xml:
  
	  <application
		....
		<service android:name="com.zqf.logservice.LogService" />

	   </application>
   
   
