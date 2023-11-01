# TensorFlow_Test
#Steps to build and run the app.
- Simply import and build the project. Make sure to have upto-dated android studio

#Any assumptions made.
- Used only single pre-defined model

#Challenges faced and how they were addressed.
- Except a few errors while setting up Tensor flow, the color for the boxes of object detected as per their category.
- Solution first i thought of to pre-define some categories and their colors but then i thought of some other solution
- At the end what i did, i converted labels of object detected into hashcodes and then converted hashcodes into RBG color. As we know hashcode will always be the same
- for the same word. so solution is simple and will work for any category.

Other thing for fine tuning the model. I can do that too but due to the shifts didn't have the enough time to complete it.
