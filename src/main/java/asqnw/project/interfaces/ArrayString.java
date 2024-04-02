package asqnw.project.interfaces;

import java.util.ArrayList;

/**
 * @Author 暗影之风
 * @CreateTime 2024-01-03 18:15 周三
 * @Description 适配C++，数组字符串
 */
@SuppressWarnings("JavadocDeclaration")
public class ArrayString
{
    private final ArrayList<String> data;

    public ArrayString()
    {
        this.data = new ArrayList<>();
    }

    public String getData(int index)
    {
        return this.data.isEmpty() ? "" : this.data.get(index);
    }

    public String getData(int index, String defaultV)
    {
        return this.data.size() - 1 < index ? defaultV : this.data.get(index);
    }

    public void putData(String str)
    {
//        if (!this.data.contains(str))
        this.data.add(str);
    }

    public int searchIndex(String value)
    {
        for (int i = 0; i < this.data.size(); i++)
        {
            if (value.equals(this.data.get(i)))
                return i;
        }
        return -1;
    }

    public String[] getAll()
    {
        String[] result = new String[this.data.size()];
        for (int i = 0; i < this.data.size(); i++)
            result[i] = this.data.get(i);
        return result;
    }

    public int length()
    {
        return this.data.size();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.data.size(); i++)
            sb.append(this.getData(i)).append(";");
        return sb.toString();
    }
}
