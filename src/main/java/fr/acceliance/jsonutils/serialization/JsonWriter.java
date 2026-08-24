package fr.acceliance.jsonutils.serialization;

import java.io.IOException;
import java.io.StringWriter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


public class JsonWriter
{
    public static String write ( Object o ) throws IOException
    {
        if ( o == null ) return ( null );
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("    ", "\n"));
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.enable ( SerializationFeature.INDENT_OUTPUT );
        StringWriter writer = new StringWriter();
        mapper.writer ( printer ) .writeValue ( writer, o );
        return ( writer .toString() );
    }
}
