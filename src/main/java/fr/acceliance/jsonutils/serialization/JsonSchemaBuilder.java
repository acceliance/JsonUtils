package fr.acceliance.jsonutils.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.List;

/**
 * Programmatic JSON Schema builder, for callers that describe their types at runtime
 * (e.g. from a UML model) instead of through compiled Java classes.
 *
 * <p>Output follows the conventions of {@link JsonSchemaGen}: draft 2020-12, one entry in
 * {@code $defs} per object or enumeration type (titled by its name), references written as
 * {@code "#/$defs/<Name>"}, {@code additionalProperties: false} on objects, and references
 * by identity key written as {@code oneOf [ key, $ref ]}.</p>
 *
 * <pre>
 * JsonSchemaBuilder b = new JsonSchemaBuilder().title( "Orders" );
 * b.defineEnum( "Status", List.of( "OPEN", "CLOSED" ), null );
 * b.defineObject( "Order" )
 *     .property( "number", b.string(), true )
 *     .property( "status", b.ref( "Status" ), false )
 *     .property( "lines", b.array( b.ref( "OrderLine" ), 1, null ), true );
 * String json = b.rootArray( "Order", null, null ).toJson();
 * </pre>
 */
public class JsonSchemaBuilder
{
    public static final String SCHEMA_DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
    public static final String DEFS_PREFIX = "#/$defs/";

    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectNode defs = mapper.createObjectNode();
    private String id;
    private String title;
    private String description;
    private String comment;
    private boolean closedObjects = true;
    private ObjectNode rootSchema;

    // ------------------------------------------------------------------
    // Document level
    // ------------------------------------------------------------------

    public JsonSchemaBuilder id ( String id )
    {
        this.id = id;
        return this;
    }

    public JsonSchemaBuilder title ( String title )
    {
        this.title = title;
        return this;
    }

    public JsonSchemaBuilder description ( String description )
    {
        this.description = description;
        return this;
    }

    /** Free text written as the document's {@code $comment}. */
    public JsonSchemaBuilder comment ( String comment )
    {
        this.comment = comment;
        return this;
    }

    /** When true (default) every object definition gets {@code additionalProperties: false}. */
    public JsonSchemaBuilder closedObjects ( boolean closed )
    {
        this.closedObjects = closed;
        return this;
    }

    /** The document root accepts {@code schema} (e.g. an {@link #anyOfRefs(List)} or an {@link #array}). */
    public JsonSchemaBuilder root ( PropertySchema schema )
    {
        rootSchema = schema.node;
        return this;
    }

    /** The document root is a single instance of definition {@code defName}. */
    public JsonSchemaBuilder rootObject ( String defName )
    {
        rootSchema = ref( defName ).node;
        return this;
    }

    /** The document root is an array of instances of definition {@code defName}. */
    public JsonSchemaBuilder rootArray ( String defName, Integer minItems, Integer maxItems )
    {
        rootSchema = array( ref( defName ), minItems, maxItems ).node;
        return this;
    }

    // ------------------------------------------------------------------
    // Definitions
    // ------------------------------------------------------------------

    public boolean hasDefinition ( String name )
    {
        return defs.has( name );
    }

    /** Returns the object definition {@code name}, creating it on first call. */
    public ObjectDefinition defineObject ( String name )
    {
        ObjectNode def = (ObjectNode) defs.get( name );
        if ( def == null )
        {
            def = defs.putObject( name );
            def.put( "type", "object" );
            def.put( "title", name );
            def.putObject( "properties" );
        }
        return new ObjectDefinition( def );
    }

    /** Declares a string enumeration definition. */
    public JsonSchemaBuilder defineEnum ( String name, Collection<String> values, String description )
    {
        ObjectNode def = defs.putObject( name );
        def.put( "type", "string" );
        def.put( "title", name );
        if ( description != null && ! description.isEmpty() ) def.put( "description", description );
        ArrayNode literals = def.putArray( "enum" );
        for ( String v : values ) literals.add( v );
        return this;
    }

    // ------------------------------------------------------------------
    // Property schemas
    // ------------------------------------------------------------------

    public PropertySchema string ()  { return typed( "string" ); }
    public PropertySchema integer () { return typed( "integer" ); }
    public PropertySchema number ()  { return typed( "number" ); }
    public PropertySchema bool ()    { return typed( "boolean" ); }

    /** Schema accepting any JSON value. */
    public PropertySchema any ()
    {
        return new PropertySchema( mapper.createObjectNode() );
    }

    /** Reference to definition {@code defName}. */
    public PropertySchema ref ( String defName )
    {
        ObjectNode n = mapper.createObjectNode();
        n.put( "$ref", DEFS_PREFIX + defName );
        return new PropertySchema( n );
    }

    /**
     * Reference accepting any of the given definitions (e.g. a base type and its subtypes).
     * A single name gives a plain {@link #ref(String)}.
     */
    public PropertySchema anyOfRefs ( List<String> defNames )
    {
        if ( defNames.size() == 1 ) return ref( defNames.get( 0 ) );
        ObjectNode n = mapper.createObjectNode();
        ArrayNode anyOf = n.putArray( "anyOf" );
        for ( String name : defNames ) anyOf.addObject().put( "$ref", DEFS_PREFIX + name );
        return new PropertySchema( n );
    }

    /**
     * Reference by identity key, in the same form as {@link JsonSchemaGen} uses for
     * {@code @JsonIdentityInfo} types: {@code oneOf [ key, target ]}.
     *
     * @param key        schema of the key value (a scalar, or an object for composite keys)
     * @param target     schema of the full object (usually a {@link #ref(String)})
     * @param targetName type name used in the key description
     */
    public PropertySchema identityReference ( PropertySchema key, PropertySchema target, String targetName )
    {
        if ( ! key.node.has( "description" ) )
            key.description( "Reference by identity key to a " + targetName + " serialised in full elsewhere" );
        ObjectNode n = mapper.createObjectNode();
        ArrayNode oneOf = n.putArray( "oneOf" );
        oneOf.add( key.node );
        oneOf.add( target.node );
        return new PropertySchema( n );
    }

    /** Inline object schema, typically for composite identity keys. */
    public ObjectDefinition inlineObject ()
    {
        ObjectNode n = mapper.createObjectNode();
        n.put( "type", "object" );
        n.putObject( "properties" );
        return new ObjectDefinition( n );
    }

    public PropertySchema array ( PropertySchema items, Integer minItems, Integer maxItems )
    {
        ObjectNode n = mapper.createObjectNode();
        n.put( "type", "array" );
        n.set( "items", items.node );
        if ( minItems != null && minItems > 0 ) n.put( "minItems", minItems );
        if ( maxItems != null ) n.put( "maxItems", maxItems );
        return new PropertySchema( n );
    }

    private PropertySchema typed ( String type )
    {
        ObjectNode n = mapper.createObjectNode();
        n.put( "type", type );
        return new PropertySchema( n );
    }

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    public ObjectNode build ()
    {
        ObjectNode doc = mapper.createObjectNode();
        doc.put( "$schema", SCHEMA_DRAFT_2020_12 );
        if ( id != null ) doc.put( "$id", id );
        if ( title != null ) doc.put( "title", title );
        if ( description != null && ! description.isEmpty() ) doc.put( "description", description );
        if ( comment != null && ! comment.isEmpty() ) doc.put( "$comment", comment );
        if ( rootSchema != null ) doc.setAll( rootSchema.deepCopy() );
        if ( defs.size() > 0 ) doc.set( "$defs", defs.deepCopy() );
        return doc;
    }

    public String toJson ()
    {
        return build().toPrettyString();
    }

    // ------------------------------------------------------------------
    // Fluent wrappers
    // ------------------------------------------------------------------

    /** A property (or item) schema under construction. */
    public static class PropertySchema
    {
        final ObjectNode node;

        PropertySchema ( ObjectNode node )
        {
            this.node = node;
        }

        public PropertySchema description ( String description )
        {
            if ( description != null && ! description.isEmpty() ) node.put( "description", description );
            return this;
        }

        public PropertySchema format ( String format )
        {
            if ( format != null ) node.put( "format", format );
            return this;
        }

        public PropertySchema maxLength ( int maxLength )
        {
            node.put( "maxLength", maxLength );
            return this;
        }

        public PropertySchema defaultValue ( String value )
        {
            if ( value != null ) node.put( "default", value );
            return this;
        }

        public ObjectNode toNode ()
        {
            return node;
        }
    }

    /** An object schema under construction (a {@code $defs} entry or an inline object). */
    public class ObjectDefinition
    {
        final ObjectNode node;

        ObjectDefinition ( ObjectNode node )
        {
            this.node = node;
            if ( closedObjects ) node.put( "additionalProperties", false );
        }

        public ObjectDefinition description ( String description )
        {
            if ( description == null || description.isEmpty() ) return this;
            // Keep "description" next to "title", ahead of "properties", for readability.
            ObjectNode rest = node.deepCopy();
            node.removeAll();
            for ( String key : new String[] { "type", "title" } )
                if ( rest.has( key ) ) node.set( key, rest.remove( key ) );
            node.put( "description", description );
            rest.remove( "description" );
            node.setAll( rest );
            return this;
        }

        public ObjectDefinition property ( String name, PropertySchema schema, boolean required )
        {
            ( (ObjectNode) node.get( "properties" ) ).set( name, schema.node );
            if ( required )
            {
                ArrayNode req = node.has( "required" ) ? (ArrayNode) node.get( "required" ) : node.putArray( "required" );
                req.add( name );
            }
            return this;
        }

        public boolean hasProperty ( String name )
        {
            return node.get( "properties" ).has( name );
        }

        /** Usable as a property schema (for inline objects). */
        public PropertySchema asSchema ()
        {
            return new PropertySchema( node );
        }
    }
}
