package tornado.collections.types;

import java.nio.FloatBuffer;

import tornado.api.Payload;
import tornado.api.Vector;
import tornado.collections.math.TornadoMath;

/**
 * Class that represents a vector of 4x floats
 * e.g. <float,float,float>
 * 
 * @author jamesclarkson
 */
@Vector
public class Float4 implements PrimitiveStorage<FloatBuffer> {

	public static final Class<Float4>	TYPE		= Float4.class;

	/**
	 * backing array
	 */
	@Payload
	final protected float[]				storage;

	/**
	 * number of elements in the storage
	 */
	final private static int			numElements	= 4;

	public Float4(float[] storage) {
		this.storage = storage;
	}

	public Float4() {
		this(new float[numElements]);
	}

	public float get(int index) {
		return storage[index];
	}

	public void set(int index, float value) {
		storage[index] = value;
	}

	public Float4(float x, float y, float z, float w) {
		this();
		setX(x);
		setY(y);
		setZ(z);
		setW(w);
	}

	public void set(Float4 value) {
		setX(value.getX());
		setY(value.getY());
		setZ(value.getZ());
		setW(value.getW());
	}

	public float getX() {
		return get(0);
	}

	public float getY() {
		return get(1);
	}

	public float getZ() {
		return get(2);
	}

	public float getW() {
		return get(3);
	}

	public void setX(float value) {
		set(0, value);
	}

	public void setY(float value) {
		set(1, value);
	}

	public void setZ(float value) {
		set(2, value);
	}

	public void setW(float value) {
		set(3, value);
	}

	/**
	 * Duplicates this vector
	 * 
	 * @return
	 */
	public Float4 duplicate() {
		final Float4 vector = new Float4();
		vector.set(this);
		return vector;
	}

	public String toString(String fmt) {
		return String.format(fmt, getX(), getY(), getZ(), getW());
	}

	public String toString() {
		return toString(FloatOps.fmt4);
	}

	/**
	 * Cast vector into a Float2
	 * 
	 * @return
	 */
	public Float2 asFloat2() {
		return new Float2(getX(), getY());
	}

	public Float3 asFloat3() {
		return new Float3(getX(), getY(), getZ());
	}

	public Float2 getLo() {
		return asFloat2();
	}

	public Float2 getHi() {
		return new Float2(getZ(), getW());
	}

	protected static final Float4 loadFromArray(final float[] array, int index) {
		final Float4 result = new Float4();
		result.setX(array[index]);
		result.setY(array[index + 1]);
		result.setZ(array[index + 2]);
		result.setW(array[index + 3]);
		return result;
	}

	protected final void storeToArray(final float[] array, int index) {
		array[index] = getX();
		array[index + 1] = getY();
		array[index + 2] = getZ();
		array[index + 3] = getW();
	}

	@Override
	public void loadFromBuffer(FloatBuffer buffer) {
		asBuffer().put(buffer);
	}

	@Override
	public FloatBuffer asBuffer() {
		return FloatBuffer.wrap(storage);
	}

	public int size() {
		return numElements;
	}

	public void fill(float value) {
		for (int i = 0; i < storage.length; i++)
			storage[i] = value;

	}

	/***
	 * Operations on Float4 vectors
	 */

	/*
	 * vector = op( vector, vector )
	 */

	public static Float4 add(Float4 a, Float4 b) {
		return new Float4(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ(), a.getW()
				+ b.getW());
	}

	public static Float4 sub(Float4 a, Float4 b) {
		return new Float4(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ(), a.getW()
				- b.getW());
	}

	public static Float4 div(Float4 a, Float4 b) {
		return new Float4(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ(), a.getW()
				/ b.getW());
	}

	public static Float4 mult(Float4 a, Float4 b) {
		return new Float4(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ(), a.getW()
				* b.getW());
	}

	public static Float4 min(Float4 a, Float4 b) {
		return new Float4(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(
				a.getZ(), b.getZ()), Math.min(a.getW(), b.getW()));
	}

	public static Float4 max(Float4 a, Float4 b) {
		return new Float4(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(
				a.getZ(), b.getZ()), Math.max(a.getW(), b.getW()));
	}

	/*
	 * vector = op (vector, scalar)
	 */
	
	public static Float4 add(Float4 a, float b){
		return new Float4(a.getX() + b,a.getY() + b,a.getZ() + b, a.getW() + b);
	}
	
	public static Float4 sub(Float4 a, float b){
		return new Float4(a.getX() - b,a.getY() - b,a.getZ() - b, a.getW() - b);
	}
	
	public static Float4 mult(Float4 a, float b){
		return new Float4(a.getX() * b,a.getY() * b,a.getZ() * b, a.getW() * b);
	}
	
	public static Float4 div(Float4 a, float b){
		return new Float4(a.getX() / b,a.getY() / b,a.getZ() / b, a.getW() / b);
	}

	/*
	 * vector = op (vector, vector, vector)
	 */
	public static void add(Float4 a, Float4 b, Float4 c) {
		c.setX(a.getX() + b.getX());
		c.setY(a.getY() + b.getY());
		c.setZ(a.getZ() + b.getZ());
		c.setW(a.getW() + b.getW());
	}

	public static void sub(Float4 a, Float4 b, Float4 c) {
		c.setX(a.getX() - b.getX());
		c.setY(a.getY() - b.getY());
		c.setZ(a.getZ() - b.getZ());
		c.setW(a.getW() - b.getW());
	}

	public static void div(Float4 a, Float4 b, Float4 c) {
		c.setX(a.getX() / b.getX());
		c.setY(a.getY() / b.getY());
		c.setZ(a.getZ() / b.getZ());
		c.setW(a.getW() / b.getW());
	}

	public static void mult(Float4 a, Float4 b, Float4 c) {
		c.setX(a.getX() * b.getX());
		c.setY(a.getY() * b.getY());
		c.setZ(a.getZ() * b.getZ());
		c.setW(a.getW() * b.getW());
	}

	public static void min(Float4 a, Float4 b, Float4 c) {
		c.setX(Math.min(a.getX(), b.getX()));
		c.setY(Math.min(a.getY(), b.getY()));
		c.setZ(Math.min(a.getZ(), b.getZ()));
		c.setW(Math.min(a.getW(), b.getW()));
	}

	public static void max(Float4 a, Float4 b, Float4 c) {
		c.setX(Math.max(a.getX(), b.getX()));
		c.setY(Math.max(a.getY(), b.getY()));
		c.setZ(Math.max(a.getZ(), b.getZ()));
		c.setW(Math.max(a.getW(), b.getW()));
	}

	/*
	 * inplace src = op (src, scalar)
	 */

	public static void inc(Float4 a, float value) {
		a.setX(a.getX() + value);
		a.setY(a.getY() + value);
		a.setZ(a.getZ() + value);
		a.setW(a.getW() + value);
	}

	public static void dec(Float4 a, float value) {
		a.setX(a.getX() - value);
		a.setY(a.getY() - value);
		a.setZ(a.getZ() - value);
		a.setW(a.getW() - value);
	}

	public static void scaleByInverse(Float4 a, float value) {
		scale(a, 1f / value);
	}

	public static void scale(Float4 a, float value) {
		a.setX(a.getX() * value);
		a.setY(a.getY() * value);
		a.setZ(a.getZ() * value);
		a.setW(a.getW() * value);
	}

	/*
	 * vector = op(vector)
	 */

	public static Float4 sqrt(Float4 a) {
		return new Float4(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()),
				TornadoMath.sqrt(a.getZ()), TornadoMath.sqrt(a.getW()));
	}

	public static Float4 floor(Float4 a) {
		return new Float4(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()),
				TornadoMath.floor(a.getZ()), TornadoMath.floor(a.getW()));
	}

	public static Float4 fract(Float4 a) {
		return new Float4(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()),
				TornadoMath.fract(a.getZ()), TornadoMath.fract(a.getW()));
	}

	/*
	 * misc inplace vector ops
	 */

	public static void clamp(Float4 x, float min, float max) {
		x.setX(TornadoMath.clamp(x.getX(), min, max));
		x.setY(TornadoMath.clamp(x.getY(), min, max));
		x.setZ(TornadoMath.clamp(x.getZ(), min, max));
		x.setW(TornadoMath.clamp(x.getW(), min, max));
	}

	public static void normalise(Float4 value) {
		final float len = length(value);
		scaleByInverse(value, len);
	}

	/*
	 * vector wide operations
	 */

	public static float min(Float4 value) {
		return Math.min(value.getX(), Math.min(value.getY(), Math.min(value.getZ(), value.getW())));
	}

	public static float max(Float4 value) {
		return Math.max(value.getX(), Math.max(value.getY(), Math.max(value.getZ(), value.getW())));
	}

	public static float dot(Float4 a, Float4 b) {
		final Float4 m = mult(a, b);
		return m.getX() + m.getY() + m.getZ() + m.getW();
	}

	/**
	 * Returns the vector length
	 * e.g. the sqrt of all elements squared
	 * 
	 * @return
	 */
	public static float length(Float4 value) {
		return TornadoMath.sqrt(dot(value, value));
	}

	public static boolean isEqual(Float4 a, Float4 b) {
		return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
	}

}