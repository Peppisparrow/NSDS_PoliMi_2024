# Evaluation lab - Node-RED

## Group number: 22

## Group members

- Paolo Gennaro
- Andrea Giangrande
- Giuseppe Vitello

## Description of message flows

    The "MQTT input 1" node receives messages about sensors in Milan. If the message includes a temperature, it is processed by the "Compute Average" node to calculate a moving average over a window of 10 values.
    It stores the value computed in a global variable called "average".

    A value is received via "MQTT input 2", and than set in a global variable "K" with the "setK" node.

    Every minute, the "timestamp" node triggers the "Milano" node, which provides weather data for the city. The "QueryOpenW" node extracts the temperature in Kelvin from the "Milano" node message. "QueryOpenW" sends a message to "ComputeDifference" which uses the "average" variable to compute the difference. 

    The result is than sent to "CompareDifference" which uses the variable "K" and if the absolute value of the difference exceeds "K," an MQTT message is sent to neslabpolimi/nsds/eval24/alarm with the text: "Temperature data is not coherent!" Otherwise, no message is sent.

## Extensions 

