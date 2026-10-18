package org.yanjing.app.semantic;

// Private bound service; all model execution stays in the :semantic process.
interface ISemanticEncoder {
    float[] encode(String text);
}
