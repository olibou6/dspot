package fr.inria.diversify.utils;

import fr.inria.diversify.util.Log;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.*;

/**
 * User: Simon
 * Date: 23/03/16
 * Time: 15:48
 */
public class TypeUtils {
    protected static Set<Class<?>> WRAPPER_TYPES = getWrapperTypes();

    public static boolean isPrimitiveCollectionOrMap(Object collectionOrMap) {
        try {
            return isPrimitiveCollection(collectionOrMap) || isPrimitiveMap(collectionOrMap);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPrimitiveCollection(Object object) {
        if (Collection.class.isInstance(object)) {
            Collection collection = (Collection) object;
            if (collection.isEmpty()) {
                return true;
            } else {
                Iterator iterator = collection.iterator();
                while (iterator.hasNext()) {
                    Object next = iterator.next();
                    if (next != null) {
                        return isPrimitive(next);
                    }
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isPrimitiveMap(Object object) {
        if(Map.class.isInstance(object)) {
            Map map = (Map) object;
            if (map.isEmpty()) {
                return true;
            } else {
                boolean isKeyPrimitive = false;
                boolean isValuePrimitive = false;
                Iterator keyIterator = map.keySet().iterator();
                while (keyIterator.hasNext()) {
                    Object next = keyIterator.next();
                    if (next != null && isPrimitive(next)) {
                        isKeyPrimitive = true;
                        break;
                    }
                }
                if (isKeyPrimitive) {
                    Iterator valueIterator = map.keySet().iterator();
                    while (valueIterator.hasNext()) {
                        Object next = valueIterator.next();
                        if (next != null && isPrimitive(next)) {
                            isValuePrimitive = true;
                            break;
                        }
                    }
                }
                return isKeyPrimitive && isValuePrimitive;
            }
        }
        return false;
    }

    public static boolean isPrimitive(Object object) {
        return isPrimitive(object.getClass());
    }

    public static boolean isPrimitive(Class cl) {
        return cl.isPrimitive()
                || isWrapperType(cl)
                || String.class.equals(cl);
    }

    public static boolean isWrapperType(Class cl) {
        return WRAPPER_TYPES.contains(cl);
    }

    protected static Set<Class<?>> getWrapperTypes() {
        Set<Class<?>> ret = new HashSet<Class<?>>();
        ret.add(Boolean.class);
        ret.add(Character.class);
        ret.add(Byte.class);
        ret.add(Short.class);
        ret.add(Integer.class);
        ret.add(Long.class);
        ret.add(Float.class);
        ret.add(Double.class);
        ret.add(Void.class);
        return ret;
    }

    public static boolean isArray(Object o) {
        return o != null && o.getClass().isArray();
    }

    public static boolean isPrimitiveArray(Object o) {
        String type = o.getClass().getCanonicalName();
        return type != null && isArray(o) &&
                (type.equals("byte[]")
                        || type.equals("short[]")
                        || type.equals("int[]")
                        || type.equals("long[]")
                        || type.equals("float[]")
                        || type.equals("double[]")
                        || type.equals("boolean[]")
                        || type.equals("char[]"));
    }

	public static boolean isSerializable(CtTypeReference type) {
		return isPrimitive(type)
				|| isString(type)
				|| isPrimitiveArray(type)
				|| isPrimitiveCollection(type)
				|| isPrimitiveMap(type);
	}

	public static boolean isPrimitive(CtTypeReference type) {
		try {
			return type.isPrimitive() || type.unbox().isPrimitive();
		} catch (Exception e) {
			return false;
		}
	}

	public static  boolean isString(CtTypeReference type) {
		try {
			return String.class.isAssignableFrom(type.getActualClass());
		} catch (Exception ignored) {
			Log.warn("Error during check isString on " + type);
		}
		return false;
	}

	public static boolean isPrimitiveArray(CtTypeReference type) {
		return CtArrayTypeReference.class.isInstance(type) && isPrimitive(((CtArrayTypeReference) type).getComponentType());
	}

	public static boolean isPrimitiveCollection(CtTypeReference type) {
		try {
			return Collection.class.isAssignableFrom(type.getActualClass());
		} catch (Exception ignored) {
			Log.warn("Error during check isPrimitiveCollection on " + type);
		}
		return false;
	}

	public static boolean isPrimitiveMap(CtTypeReference type) {
		try {
			return Map.class.isAssignableFrom(type.getActualClass());
		} catch (Exception ignored) {
			Log.warn("Error during check isPrimitiveMap on " + type);
		}
		return false;
	}
}