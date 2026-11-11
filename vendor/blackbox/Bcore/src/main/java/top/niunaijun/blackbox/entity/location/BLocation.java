// Modified for Campus Atlas; see vendor/blackbox/NOTICE-ATLAS.md.

package top.niunaijun.blackbox.entity.location;

import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;


public class BLocation implements Parcelable {

    private double mLatitude = 0.0;
    private double mLongitude = 0.0;
    private double mAltitude = 0.0f;
    private float mSpeed = 0.0f;
    private float mBearing = 0.0f;
    private float mAccuracy = 0.0f;





    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(this.mLatitude);
        dest.writeDouble(this.mLongitude);
        dest.writeDouble(this.mAltitude);
        dest.writeFloat(this.mSpeed);
        dest.writeFloat(this.mBearing);
        dest.writeFloat(this.mAccuracy);
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public BLocation() {
    }

    public BLocation(double latitude, double mLongitude) {
        this.mLatitude = latitude;
        this.mLongitude = mLongitude;
    }

    public BLocation(double latitude, double longitude, float speed, float bearing) {
        this(latitude, longitude);
        if (!Float.isFinite(speed) || speed < 0 || !Float.isFinite(bearing) || bearing < 0 || bearing >= 360)
            throw new IllegalArgumentException("Invalid motion metadata");
        this.mSpeed = speed;
        this.mBearing = bearing;
    }

    public BLocation(Parcel in) {
        this.mLatitude = in.readDouble();
        this.mLongitude = in.readDouble();
        this.mAltitude = in.readDouble();
        this.mSpeed = in.readFloat();
        this.mBearing = in.readFloat();
        this.mAccuracy = in.readFloat();
    }

    public boolean isEmpty() {
        return mLatitude == 0 && mLongitude == 0;
    }

    public static final Parcelable.Creator<BLocation> CREATOR = new Parcelable.Creator<BLocation>() {
        @Override
        public BLocation createFromParcel(Parcel source) {
            return new BLocation(source);
        }

        @Override
        public BLocation[] newArray(int size) {
            return new BLocation[size];
        }
    };

    @Override
    public String toString() {
        return "BLocation{" +
                "latitude: " + mLatitude +
                ", longitude: " + mLongitude +
                ", altitude: " + mAltitude +
                ", speed: " + mSpeed +
                ", bearing: " + mBearing +
                ", accuracy: " + mAccuracy +
                '}';
    }

    public Location convert2SystemLocation() {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(mLatitude);
        location.setLongitude(mLongitude);
        location.setSpeed(mSpeed);
        location.setBearing(mBearing);
        location.setAccuracy(40f);
        location.setTime(System.currentTimeMillis());
        location.setAltitude(mAltitude);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            location.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
        }
        Bundle extraBundle = new Bundle();
        
        int satelliteCount = 10;
        extraBundle.putInt("satellites", satelliteCount);
        extraBundle.putInt("satellitesvalue", satelliteCount);
        location.setExtras(extraBundle);
        return location;
    }
}
