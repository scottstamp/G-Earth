package g_earth.misc;

/**
 * Created by Jonas on 14/06/18.
 * This interface defines an object which can FULLY be represented as a String (all fields). An object must be able to be recreated having the String representation
 */
public interface StringifyAble {

    String stringify();

    //the object before calling this function will typically have no real point, this is some kind of constructor
    // (this implies that the Object should probably have a constructor just calling this function)
    void constructFromString(String str);

}
