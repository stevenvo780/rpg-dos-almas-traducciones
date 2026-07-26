# Desbloquea el recetario nativo entero para el jugador que acaba de entrar.
#
# El librito verde de la mesa y del inventario solo ensena las recetas que el
# jugador tiene DESBLOQUEADAS, y cada mod las va soltando con su propio avance:
# la mejora avanzada de recogida de la mochila, por ejemplo, solo aparece cuando
# ya llevas encima la basica. Con 136 mods eso deja el recetario medio vacio.
#
# Lo lanza el avance dos_almas:desbloquear_recetas, que no tiene display y por
# tanto no saca notificacion ni cuenta como logro. Salta una sola vez por jugador.
#
# No afecta a las misiones: las 215 tareas de tipo advancement del arbol de FTB
# son de juego (aether:aether_sleep y companía), ninguna es un avance de receta.
recipe give @s *
