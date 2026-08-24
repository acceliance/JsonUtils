package fr.acceliance.jsonutils.serialization;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.*;

import java.util.LinkedHashMap;
import java.util.Map;

class VisitorForComments implements JsonFormatVisitorWrapper
{
    private final ObjectMapper mapper;
    private final Map<String, Object> example = new LinkedHashMap<>();

    public VisitorForComments (ObjectMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    public JsonObjectFormatVisitor expectObjectFormat(JavaType type) throws JsonMappingException
    {
        return new JsonObjectFormatVisitor.Base()
        {
            @Override
            public void optionalProperty(BeanProperty prop) throws JsonMappingException
            {
                JsonPropertyDescription a = prop.getAnnotation(JsonPropertyDescription.class);
                if (a != null) {
                    example.put(prop.getName(), mapper.convertValue(a.value(), prop.getType()));
                } else {
                    example.put(prop.getName(), prop.getType().toCanonical());
                }
            }
        };
    }

    @Override
    public JsonArrayFormatVisitor expectArrayFormat(JavaType type) throws JsonMappingException {
        return null;
    }

    @Override
    public JsonStringFormatVisitor expectStringFormat(JavaType type) throws JsonMappingException {
        return null;
    }

    @Override
    public JsonNumberFormatVisitor expectNumberFormat(JavaType type) throws JsonMappingException {
        return null;
    }

    @Override
    public JsonIntegerFormatVisitor expectIntegerFormat(JavaType type) throws JsonMappingException {
        return null;
    }

    @Override
    public JsonBooleanFormatVisitor expectBooleanFormat(JavaType type) throws JsonMappingException {
        return null;
    }

    @Override
    public JsonNullFormatVisitor expectNullFormat(JavaType type) throws JsonMappingException {
        return null;
    }

    @Override
    public JsonAnyFormatVisitor expectAnyFormat(JavaType type) throws JsonMappingException {
        return null;
    }

    @Override
    public JsonMapFormatVisitor expectMapFormat(JavaType type) throws JsonMappingException {
        return null;
    }

    @Override
    public SerializerProvider getProvider() {
        return null;
    }

    @Override
    public void setProvider(SerializerProvider provider) {

    }

    public Map<String,Object> getExample() {
        return example;
    }
}