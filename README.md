# LogServiceMaster
logService


Step 1. Add the JitPack repository to your build file
Add it in your root build.gradle at the end of repositories:


	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
  
  
  Step 2. Add the dependency
  dependencies {
	        implementation 'com.github.zhangqufa:LogServiceMaster:Tag'
	}
  
  Step 3. register Service in AndroidMainfest.xml
  <application
        ....
        <service android:name="com.zqf.logservice.LogService" />

   </application>
