package fr.acceliance.jsonutils.serialization;

import java.io.IOException;
import java.io.StringReader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.core.report.ProcessingReport;

public class JsonReader
{
    public static <T> T read ( String json, Class<T> clazz ) throws IOException
    {
        if ( json == null || clazz == null ) return ( null );
        T ret = null;
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        StringReader reader = new StringReader( json );
        ret = mapper.readerFor(clazz).readValue ( reader );
        return ( ret );
    }
}
