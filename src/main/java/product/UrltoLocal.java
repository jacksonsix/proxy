package product;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class UrltoLocal {
    static Set<String> Memo = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
    		"abc",
    		"def"
    		)));
	public static boolean isLocal(String path) {
		return Memo.contains(path);
	}
}
