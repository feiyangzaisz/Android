package cn.hurry.util;

public class CodeRuntimeException extends RuntimeException
{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private String            code             = null;

    public CodeRuntimeException(String code)
    {
        if (code == null)
            throw new NullPointerException();
        this.code = code;
    }

    public CodeRuntimeException(String code, String detailMessage)
    {
        super(detailMessage);
        if (code == null)
            throw new NullPointerException();
        this.code = code;
    }

    public CodeRuntimeException(String code, Throwable throwable)
    {
        super(throwable);
        if (code == null)
            throw new NullPointerException();
        this.code = code;
    }

    public CodeRuntimeException(String code, String detailMessage, Throwable throwable)
    {
        super(detailMessage, throwable);
        if (code == null)
            throw new NullPointerException();
        this.code = code;
    }

    public String getCode()
    {
        return code;
    }

    @Override
    public String toString()
    {
        // TODO Auto-generated method stub
        String name = getClass().getName();
        String msg = StringUtilities.toStringWhenNull(getLocalizedMessage(), "");
        return name + ": [code:" + code + "]" + msg;
    }

}
