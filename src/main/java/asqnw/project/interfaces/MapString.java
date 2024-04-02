package asqnw.project.interfaces;

/**
 * @Author 暗影之风
 * @CreateTime 2024-01-05 12:01 周五
 * @Description 键值对存储
 */
@SuppressWarnings("JavadocDeclaration")
public class MapString
{
    private final ArrayString keys;
    private final ArrayString value;

    public MapString()
    {
        this.keys = new ArrayString();
        this.value = new ArrayString();
    }

    public String get(String key)
    {
        return this.value.getData(this.keys.searchIndex(key), "");
    }

    public void put(String key, String value)
    {
        if (this.keys.searchIndex(key) != -1)
            return;
        this.keys.putData(key);
        this.value.putData(value);
    }

    public String[] keySet()
    {
        return this.keys.getAll();
    }

    public boolean isEmpty()
    {
        return this.length() == 0;
    }

    public int length()
    {
        return this.keys.length();
    }
}
