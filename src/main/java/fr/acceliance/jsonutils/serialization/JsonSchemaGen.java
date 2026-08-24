package fr.acceliance.jsonutils.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class JsonSchemaGen
{
    private static final String DEFS_PREFIX = "#/$defs/";

    public static String generateSchema ( Class<?> type )
    {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12,
                OptionPreset.PLAIN_JSON
        );

        configBuilder.with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED));

        configBuilder.forTypesInGeneral()
                .withTitleResolver(scope -> scope.getType().getErasedType().getSimpleName());

        configBuilder.with(
                Option.DEFINITIONS_FOR_ALL_OBJECTS,
                Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT
        );

        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(config);

        JsonNode schemaNode = generator.generateSchema(type);

        // victools has no knowledge of Jackson's @JsonIdentityInfo. For types that use a
        // PropertyGenerator, the first occurrence is serialised in full and every later
        // occurrence is written as the bare identity value (the "name" string). The raw
        // schema declares those references as object-only, so it wrongly rejects the
        // by-name form. Rewrite every $ref to such a type into oneOf[string, object].
        applyIdentityReferences( schemaNode, collectIdentityTypes( type, schemaNode ) );

        return schemaNode.toPrettyString();
    }

    /**
     * Collects the simple names of the schema definitions whose backing class is annotated
     * with {@link JsonIdentityInfo} using a {@link ObjectIdGenerators.PropertyGenerator}.
     * Definition names produced by the title resolver are the classes' simple names, so we
     * resolve them against the root type's package.
     */
    private static Set<String> collectIdentityTypes ( Class<?> rootType, JsonNode schemaNode )
    {
        Set<String> result = new HashSet<>();
        String pkg = rootType.getPackage() != null ? rootType.getPackage().getName() : "";

        if ( usesPropertyIdentity( rootType ) ) result.add( rootType.getSimpleName() );

        JsonNode defs = schemaNode.get( "$defs" );
        if ( defs == null ) defs = schemaNode.get( "definitions" ); // fallback for other drafts
        if ( defs != null )
        {
            for ( Iterator<String> it = defs.fieldNames(); it.hasNext(); )
            {
                String name = it.next();
                Class<?> c = resolveClass( pkg, name );
                if ( c != null && usesPropertyIdentity( c ) ) result.add( name );
            }
        }
        return result;
    }

    private static Class<?> resolveClass ( String pkg, String simpleName )
    {
        try
        {
            String fqn = ( pkg == null || pkg.isEmpty() ) ? simpleName : pkg + "." + simpleName;
            return Class.forName( fqn );
        }
        catch ( ClassNotFoundException e )
        {
            return null; // definition without a matching class in the root package (e.g. enums) — skip
        }
    }

    private static boolean usesPropertyIdentity ( Class<?> c )
    {
        JsonIdentityInfo ann = c.getAnnotation( JsonIdentityInfo.class );
        return ann != null && ann.generator() == ObjectIdGenerators.PropertyGenerator.class;
    }

    /**
     * Recursively rewrites every {@code {"$ref": "#/$defs/<identity type>"}} node into
     * {@code {"oneOf": [ {string reference}, {"$ref": ...} ]}}, preserving any sibling
     * keywords (such as {@code description}). Definition bodies themselves are objects, not
     * $ref nodes, so they are left untouched; only references to a type are wrapped.
     */
    private static void applyIdentityReferences ( JsonNode node, Set<String> identityTypes )
    {
        if ( identityTypes.isEmpty() ) return;

        if ( node instanceof ObjectNode )
        {
            ObjectNode obj = (ObjectNode) node;
            JsonNode ref = obj.get( "$ref" );
            if ( ref != null && ref.isTextual() )
            {
                String targetName = definitionName( ref.asText() );
                if ( targetName != null && identityTypes.contains( targetName ) )
                {
                    wrapAsIdentityReference( obj, ref.asText(), targetName );
                    return; // do not recurse into the freshly created oneOf (it holds the same $ref)
                }
            }
            for ( JsonNode child : obj ) applyIdentityReferences( child, identityTypes );
        }
        else if ( node instanceof ArrayNode )
        {
            for ( JsonNode child : node ) applyIdentityReferences( child, identityTypes );
        }
    }

    private static void wrapAsIdentityReference ( ObjectNode obj, String refValue, String targetName )
    {
        obj.remove( "$ref" );

        ObjectNode stringSchema = obj.objectNode();
        stringSchema.put( "type", "string" );
        stringSchema.put( "description",
                "Reference by identity key (name) to a " + targetName + " serialised in full earlier in the document" );

        ObjectNode refSchema = obj.objectNode();
        refSchema.put( "$ref", refValue );

        ArrayNode oneOf = obj.arrayNode();
        oneOf.add( stringSchema );
        oneOf.add( refSchema );
        obj.set( "oneOf", oneOf );
    }

    private static String definitionName ( String ref )
    {
        return ref.startsWith( DEFS_PREFIX ) ? ref.substring( DEFS_PREFIX.length() ) : null;
    }
}
